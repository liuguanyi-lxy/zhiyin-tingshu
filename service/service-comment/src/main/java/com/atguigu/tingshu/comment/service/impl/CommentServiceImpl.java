package com.atguigu.tingshu.comment.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.tingshu.comment.service.CommentService;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import com.atguigu.tingshu.common.rabbit.service.RabbitService;
import com.atguigu.tingshu.common.util.SensitiveWordFilterUtil;
import com.atguigu.tingshu.model.comment.Comment;
import com.atguigu.tingshu.model.comment.CommentPraise;
import com.atguigu.tingshu.user.client.UserInfoFeignClient;
import com.atguigu.tingshu.vo.album.AlbumStatMqVo;
import com.atguigu.tingshu.vo.album.TrackStatMqVo;
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
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
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

		// 一次性查出这些顶层评论下的全部回复（含"回复的回复"），避免列表页 N+1 查询
		List<String> topIds = topComments.stream().map(Comment::getId).collect(Collectors.toList());
		List<Comment> replies = findAllReplies(topIds);
		if (!CollectionUtils.isEmpty(replies)) {
			// 沿 replyCommentId 链上溯定位每条回复所属的顶层评论，并按时间正序挂载
			Map<String, Comment> replyById = replies.stream()
					.collect(Collectors.toMap(Comment::getId, Function.identity(), (a, b) -> a));
			Map<String, List<Comment>> replyGroups = new HashMap<>();
			for (Comment reply : replies) {
				Comment parent = replyById.get(reply.getReplyCommentId());
				// parent 非空说明是"回复的回复"：填充 parent 供前端展示"甲 回复 乙"
				reply.setParent(parent);
				String topId = reply.getReplyCommentId();
				Comment cursor = parent;
				Set<String> seen = new HashSet<>();
				while (cursor != null && seen.add(cursor.getId())) {
					topId = cursor.getReplyCommentId();
					cursor = replyById.get(topId);
				}
				replyGroups.computeIfAbsent(topId, k -> new ArrayList<>()).add(reply);
			}
			topComments.forEach(comment -> comment.setReplyCommentList(
					replyGroups.getOrDefault(comment.getId(), new ArrayList<>())));
		}

		// 标记"我发的评论"可删除（前端 deleteMark==="1" 才渲染删除按钮）
		topComments.forEach(comment -> comment.setDeleteMark(isOwn(comment, userId) ? "1" : "0"));
		replies.forEach(reply -> reply.setDeleteMark(isOwn(reply, userId) ? "1" : "0"));

		// 当前用户的点赞状态：一次查询覆盖顶层与回复
		Set<String> praisedIds = findPraisedCommentIds(userId, topIds, replies);
		topComments.forEach(comment -> comment.setIsPraise(praisedIds.contains(comment.getId())));
		replies.forEach(reply -> reply.setIsPraise(praisedIds.contains(reply.getId())));

		return buildPage(page, limit, total, topComments);
	}

	/**
	 * 向下逐层批量搜集回复（覆盖直接回复与"回复的回复"；最多 5 层并带 visited 防环防御），
	 * 结果按 createTime 正序返回。
	 */
	private List<Comment> findAllReplies(List<String> topIds) {
		List<Comment> all = new ArrayList<>();
		Set<String> visited = new HashSet<>();
		List<String> parentIds = topIds;
		for (int depth = 0; depth < 5 && !CollectionUtils.isEmpty(parentIds); depth++) {
			List<Comment> level = mongoTemplate.find(
					Query.query(Criteria.where("replyCommentId").in(parentIds))
							.with(Sort.by(Sort.Direction.ASC, "createTime")),
					Comment.class);
			level = level.stream().filter(item -> visited.add(item.getId())).collect(Collectors.toList());
			if (level.isEmpty()) {
				break;
			}
			all.addAll(level);
			parentIds = level.stream().map(Comment::getId).collect(Collectors.toList());
		}
		all.sort(Comparator.comparing(Comment::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder())));
		return all;
	}

	/** 是否为当前用户自己发的评论（匿名或不同用户返回 false） */
	private boolean isOwn(Comment comment, Long userId) {
		return userId != null && Objects.equals(comment.getUserId(), userId);
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

		// 只有顶层评论参与统计（回复不计）：专辑评论数 0404 + 声音评论数 0704（仅声音级评论）
		if (TOP_LEVEL_REPLY_ID.equals(comment.getReplyCommentId())) {
			sendAlbumStatUpdate(comment.getAlbumId(), 1);
			sendTrackStatUpdate(comment.getTrackId(), comment.getAlbumId(), 1);
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

	/** 声音维度评论数变更（0704）：仅声音级评论计入，专辑级评论(trackId=0)跳过 */
	private void sendTrackStatUpdate(Long trackId, Long albumId, int count) {
		if (trackId == null || trackId <= ALBUM_LEVEL_TRACK_ID) {
			return;
		}
		TrackStatMqVo statMqVo = new TrackStatMqVo();
		statMqVo.setBusinessNo(UUID.randomUUID().toString());
		statMqVo.setAlbumId(albumId);
		statMqVo.setTrackId(trackId);
		statMqVo.setStatType(SystemConstant.TRACK_STAT_COMMENT);
		statMqVo.setCount(count);
		rabbitService.sendMessage(MqConst.EXCHANGE_TRACK, MqConst.ROUTING_TRACK_STAT_UPDATE, JSON.toJSONString(statMqVo));
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

		// 顶层评论删除才扣减统计（与发布侧语义对称）：专辑 0404 + 声音 0704
		if (TOP_LEVEL_REPLY_ID.equals(comment.getReplyCommentId())) {
			sendAlbumStatUpdate(comment.getAlbumId(), -1);
			sendTrackStatUpdate(comment.getTrackId(), comment.getAlbumId(), -1);
		}
		log.info("[评论] 用户{}删除评论 {}，连带回复 {} 条", userId, commentId, replies.size());
	}
}
