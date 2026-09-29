package com.atguigu.tingshu.comment.service;

import com.atguigu.tingshu.model.comment.Comment;
import com.atguigu.tingshu.vo.comment.CommentVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface CommentService {

	/**
	 * 分页查询某专辑（或某声音）的评论，含一级回复与当前用户点赞状态（匿名访问时 isPraise 恒为 false）
	 */
	Page<Comment> findCommentPage(Long albumId, Long trackId, Long userId, Integer page, Integer limit);

	/**
	 * 发表评论/回复：敏感词过滤 + 用户信息冗余 + 顶层评论异步累加专辑评论数
	 */
	void saveComment(Long userId, CommentVo commentVo);

	/**
	 * 点赞/取消点赞（幂等切换：未赞则赞并 +1，已赞则取消并 -1）
	 */
	void praise(Long userId, Long albumId, String commentId);

	/**
	 * 删除自己的评论（连带其回复与点赞记录），顶层评论异步扣减专辑评论数
	 */
	void removeComment(Long userId, Long albumId, String commentId);
}
