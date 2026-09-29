package com.atguigu.tingshu.comment.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.tingshu.comment.service.CommentService;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import com.atguigu.tingshu.common.rabbit.service.RabbitService;
import com.atguigu.tingshu.common.util.SensitiveWordFilterUtil;
import com.atguigu.tingshu.model.comment.Comment;
import com.atguigu.tingshu.model.comment.CommentPraise;
import com.atguigu.tingshu.user.client.UserInfoFeignClient;
import com.atguigu.tingshu.vo.album.AlbumStatMqVo;
import com.atguigu.tingshu.vo.comment.CommentVo;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 评论业务实现（MongoDB）。
 *
 * <p>设计要点：
 * 1. 内容经 DFA 敏感词库过滤后落库；用户昵称（脱敏）与头像在发布时冗余进评论，列表页零 Feign；
 * 2. 一级评论 replyCommentId="0"，回复指向被回复评论 id；专辑级评论 trackId 归一为 0；
 * 3. 顶层评论的增删通过 MQ 异步累加/扣减专辑评论数（album_stat 0404），businessNo 防重；
 * 4. 点赞以 comment_praise 集合（commentId+userId）为唯一事实，praiseCount 为冗余计数。</p>
 */
@Slf4j
@Service
@SuppressWarnings({"all"})
public class CommentServiceImpl implements CommentService {

	/** 一级评论的 replyCommentId 约定值（与 CommentVo 注释、前端约定一致） */
	private static final String TOP_LEVEL_REPLY_ID = "0";

	/** 专辑级评论的 trackId 归一值 */
	private static final long ALBUM_LEVEL_TRACK_ID = 0L;

	@Autowired
	private MongoTemplate mongoTemplate;

	@Autowired
	private RabbitService rabbitService;

	@Autowired
	private UserInfoFeignClient userInfoFeignClient;

	@Override
	public Page<Comment> findCommentPage(Long albumId, Long trackId, Long userId, Integer page, Integer limit) {
		Long normalizedTrackId = trackId == null ? ALBUM_LEVEL_TRACK_ID : trackId;
		Query topQuery = Query.query(Criteria.where("albumId").is(albumId)
				.and("trackId").is(normalizedTrackId)
				.and("replyCommentId").is(TOP_LEVEL_REPLY_ID));
		long total = mongoTemplate.count(topQuery, Comment.class);
		topQuery.with(Sort.by(Sort.Direction.DESC, "createTime"))
				.skip((long) (page - 1) * limit)
				.limit(limit);
		List<Comment> topComments = mongoTemplate.find(topQuery, Comment.class);

		if (CollectionUtils.isEmpty(topComments)) {
			return buildPage(page, limit, total, topComments);
		}

		// 一次性查出全部一级回复，避免列表页 N+1 查询
		List<String> topIds = topComments.stream().map(Comment::getId).collect(Collectors.toList());
		List<Comment> replies = mongoTemplate.find(
				Query.query(Criteria.where("replyCommentId").in(topIds)), Comment.class);
		if (!CollectionUtils.isEmpty(replies)) {
			topComments.forEach(comment -> comment.setReplyCommentList(replies.stream()
					.filter(reply -> comment.getId().equals(reply.getReplyCommentId()))
					.collect(Collectors.toList())));
		}

		// 当前用户的点赞状态：一次查询覆盖顶层与回复
		Set<String> praisedIds = findPraisedCommentIds(userId, topIds, replies);
		topComments.forEach(comment -> comment.setIsPraise(praisedIds.contains(comment.getId())));
		replies.forEach(reply -> reply.setIsPraise(praisedIds.contains(reply.getId())));

		return buildPage(page, limit, total, topComments);
	}

	private Set<String> findPraisedCommentIds(Long userId, List<String> topIds, List<Comment> replies) {
		if (userId == null) {
			return Set.of();
		}
		List<String> allIds = new ArrayList<>(topIds);
		replies.forEach(reply -> allIds.add(reply.getId()));
		List<CommentPraise> praises = mongoTemplate.find(
				Query.query(Criteria.where("userId").is(userId).and("commentId").in(allIds)), CommentPraise.class);
		return praises.stream().map(CommentPraise::getCommentId).collect(Collectors.toSet());
	}

	private Page<Comment> buildPage(Integer page, Integer limit, long total, List<Comment> records) {
		Page<Comment> result = new Page<>(page, limit);
		result.setTotal(total);
		result.setRecords(records);
		return result;
	}

	@Override
	public void saveComment(Long userId, CommentVo commentVo) {
		Assert.notNull(userId, "用户未登录");
		Assert.notNull(commentVo.getAlbumId(), "专辑id不能为空");
		Assert.hasText(commentVo.getContent(), "评论内容不能为空");

		Comment comment = new Comment();
		comment.setAlbumId(commentVo.getAlbumId());
		// 专辑级评论 trackId 统一存 0，查询侧才能用等值匹配
		comment.setTrackId(commentVo.getTrackId() == null ? ALBUM_LEVEL_TRACK_ID : commentVo.getTrackId());
		comment.setUserId(userId);
		// DFA 敏感词过滤
		comment.setContent(SensitiveWordFilterUtil.replaceSensitiveWord(commentVo.getContent()));
		comment.setReplyCommentId(StringUtils.hasText(commentVo.getReplyCommentId())
				? commentVo.getReplyCommentId() : TOP_LEVEL_REPLY_ID);
		comment.setAlbumCommentScore(commentVo.getAlbumCommentScore());
		comment.setPraiseCount(0);
		comment.setCreateTime(new Date());

		// 昵称（脱敏）与头像冗余进评论，列表页不再逐条反查用户服务
		fillUserInfo(userId, comment);

		mongoTemplate.save(comment);

		// 只有顶层评论参与专辑评论数统计（回复不计），异步累加 0404
		if (TOP_LEVEL_REPLY_ID.equals(comment.getReplyCommentId())) {
			sendAlbumStatUpdate(comment.getAlbumId(), 1);
		}
		log.info("[评论] 用户{}发布评论成功 albumId={}, trackId={}, reply={}",
				userId, comment.getAlbumId(), comment.getTrackId(), comment.getReplyCommentId());
	}

	private void fillUserInfo(Long userId, Comment comment) {
		// 默认昵称兜底：用户服务降级时评论照发，只是不展示真实昵称
		comment.setNickname("听友");
		try {
			var result = userInfoFeignClient.getUserInfoVo(userId);
			UserInfoVo userInfo = result == null ? null : result.getData();
			if (userInfo != null) {
				comment.setNickname(maskNickname(userInfo.getNickname()));
				comment.setAvatarUrl(userInfo.getAvatarUrl());
			}
		} catch (Exception e) {
			// 用户服务降级不阻塞评论发布
			log.warn("[评论] 获取用户信息失败，昵称使用兜底值 userId={}", userId, e);
		}
	}

	private String maskNickname(String nickname) {
		if (!StringUtils.hasText(nickname)) {
			return "听友";
		}
		return nickname.length() <= 1 ? nickname + "**" : nickname.charAt(0) + "**";
	}

	private void sendAlbumStatUpdate(Long albumId, int count) {
		AlbumStatMqVo statMqVo = new AlbumStatMqVo();
		statMqVo.setAlbumId(albumId);
		statMqVo.setStatType(com.atguigu.tingshu.common.constant.SystemConstant.ALBUM_STAT_COMMENT);
		statMqVo.setCount(count);
		statMqVo.setBusinessNo(UUID.randomUUID().toString());
		rabbitService.sendMessage(MqConst.EXCHANGE_ALBUM, MqConst.ROUTING_ALBUM_STAT_UPDATE, JSON.toJSONString(statMqVo));
	}

	@Override
	public void praise(Long userId, Long albumId, String commentId) {
		Assert.notNull(userId, "用户未登录");
		Comment comment = mongoTemplate.findById(commentId, Comment.class);
		Assert.notNull(comment, "评论不存在");

		CommentPraise existing = mongoTemplate.findOne(
				Query.query(Criteria.where("commentId").is(commentId).and("userId").is(userId)), CommentPraise.class);
		if (existing == null) {
			CommentPraise praise = new CommentPraise();
			praise.setAlbumId(albumId);
			praise.setCommentId(commentId);
			praise.setUserId(userId);
			praise.setCreateTime(new Date());
			mongoTemplate.insert(praise);
			incPraiseCount(commentId, 1);
		} else {
			mongoTemplate.remove(existing);
			incPraiseCount(commentId, -1);
		}
	}

	/** 计数下界保护：扣减时 praiseCount>0 才生效，避免冗余计数减成负数 */
	private void incPraiseCount(String commentId, int delta) {
		Query query = Query.query(Criteria.where("_id").is(commentId));
		if (delta < 0) {
			query.addCriteria(Criteria.where("praiseCount").gt(0));
		}
		mongoTemplate.updateFirst(query, new Update().inc("praiseCount", delta), Comment.class);
	}

	@Override
	public void removeComment(Long userId, Long albumId, String commentId) {
		Assert.notNull(userId, "用户未登录");
		Comment comment = mongoTemplate.findById(commentId, Comment.class);
		Assert.notNull(comment, "评论不存在");
		if (!Objects.equals(comment.getUserId(), userId)) {
			// 越权删除防护：只能删除自己的评论
			throw new GuiguException(203, "只能删除自己的评论");
		}

		// 删除回复及两层点赞记录
		List<Comment> replies = mongoTemplate.find(
				Query.query(Criteria.where("replyCommentId").is(commentId)), Comment.class);
		if (!CollectionUtils.isEmpty(replies)) {
			List<String> replyIds = replies.stream().map(Comment::getId).collect(Collectors.toList());
			mongoTemplate.remove(Query.query(Criteria.where("commentId").in(replyIds)), CommentPraise.class);
			mongoTemplate.remove(Query.query(Criteria.where("_id").in(replyIds)), Comment.class);
		}
		mongoTemplate.remove(Query.query(Criteria.where("commentId").is(commentId)), CommentPraise.class);
		mongoTemplate.remove(comment);

		// 顶层评论删除才扣减统计（与发布侧语义对称）
		if (TOP_LEVEL_REPLY_ID.equals(comment.getReplyCommentId())) {
			sendAlbumStatUpdate(comment.getAlbumId(), -1);
		}
		log.info("[评论] 用户{}删除评论 {}，连带回复 {} 条", userId, commentId, replies.size());
	}
}
