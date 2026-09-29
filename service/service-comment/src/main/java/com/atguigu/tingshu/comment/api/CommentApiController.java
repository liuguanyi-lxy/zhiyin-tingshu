package com.atguigu.tingshu.comment.api;

import com.atguigu.tingshu.comment.service.CommentService;
import com.atguigu.tingshu.common.login.LoginVerification;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.vo.comment.CommentVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评论接口。路径与小程序前端 api/comment/comment.js 一一对应：
 * 浏览评论匿名可看（网关白名单），发布/点赞/删除必须登录。
 */
@Tag(name = "专辑评论管理")
@RestController
@RequestMapping("/api/comment")
@Slf4j
public class CommentApiController {

	@Autowired
	private CommentService commentService;

	/**
	 * 评论分页列表。匿名可访问；携带 token 时返回当前用户点赞状态（isPraise）。
	 *
	 * @param trackId 声音id，专辑级评论传 0
	 */
	@LoginVerification(required = false)
	@Operation(summary = "评论分页列表")
	@GetMapping("/findCommentPage/{albumId}/{trackId}/{page}/{limit}")
	public Result findCommentPage(@PathVariable Long albumId,
	                              @PathVariable Long trackId,
	                              @PathVariable Integer page,
	                              @PathVariable Integer limit) {
		Long userId = AuthContextHolder.getUserId();
		return Result.ok(commentService.findCommentPage(albumId, trackId, userId, page, limit));
	}

	@LoginVerification
	@Operation(summary = "发表评论/回复")
	@PostMapping("/save")
	public Result save(@RequestBody @Validated CommentVo commentVo) {
		Long userId = AuthContextHolder.getUserId();
		commentService.saveComment(userId, commentVo);
		return Result.ok();
	}

	@LoginVerification
	@Operation(summary = "点赞/取消点赞")
	@GetMapping("/praise/{albumId}/{commentId}")
	public Result praise(@PathVariable Long albumId, @PathVariable String commentId) {
		Long userId = AuthContextHolder.getUserId();
		commentService.praise(userId, albumId, commentId);
		return Result.ok();
	}

	@LoginVerification
	@Operation(summary = "删除自己的评论")
	@GetMapping("/remove/{albumId}/{commentId}")
	public Result remove(@PathVariable Long albumId, @PathVariable String commentId) {
		Long userId = AuthContextHolder.getUserId();
		commentService.removeComment(userId, albumId, commentId);
		return Result.ok();
	}
}
