package com.atguigu.tingshu.user.api;

import com.atguigu.tingshu.common.login.LoginVerification;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.model.user.UserListenProcess;
import com.atguigu.tingshu.user.service.UserListenProcessService;
import com.atguigu.tingshu.vo.user.UserListenProcessListVo;
import com.atguigu.tingshu.vo.user.UserListenProcessVo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@Tag(name = "用户声音播放进度管理接口")
@RestController
@RequestMapping("api/user/userListenProcess")
@SuppressWarnings({"all"})
public class UserListenProcessApiController {

	@Autowired
	private UserListenProcessService userListenProcessService;
	//保存（更新）声音播放进度
	@LoginVerification
	@Operation(summary = "根据前端传入的专辑ID、声音ID、播放进度更新或者保存指定用户指定声音的播放进度,前端每5s调用一次该接口")
	@PostMapping("/updateListenProcess")
	public Result
	updateListenProcess(@RequestBody UserListenProcessVo userListenProcessVo) {
		//获取userId,添加了@LoginVerification就会进行登录校验，就会获取到用户ID存储到LocalThread
		Long userId = AuthContextHolder.getUserId();
		userListenProcessService.saveOrUpdateListenProcess(userId,userListenProcessVo);
		return Result.ok();
	}
	/**
	 * 获取声音播放进度
	 * @param trackId
	 * @return
	 */
	@LoginVerification
	@Operation(summary = "获取声音的上次跳出时间")
	@GetMapping("/getTrackBreakSecond/{trackId}")
	public Result<BigDecimal> getTrackBreakSecond(@PathVariable Long trackId) {
		// 获取用户Id
		Long userId = AuthContextHolder.getUserId();
		// 调用服务层方法
		BigDecimal trackBreakSecond = userListenProcessService.getTrackBreakSecond(userId, trackId);
		// 返回数据
		return Result.ok(trackBreakSecond);
	}

	/**
	 * 分页查询"我的收听历史"，供前端"我的"页历史 Tab 使用。
	 */
	@LoginVerification
	@Operation(summary = "分页查询我的收听历史")
	@GetMapping("/findUserPage/{page}/{limit}")
	public Result<IPage<UserListenProcessListVo>> findUserPage(@PathVariable Long page, @PathVariable Long limit) {
		Long userId = AuthContextHolder.getUserId();
		return Result.ok(userListenProcessService.findUserPage(page, limit, userId));
	}

	/**
	 * 删除指定收听历史（软删除 isShow=0），前端历史列表的删除按钮调用。
	 */
	@LoginVerification
	@Operation(summary = "删除指定收听历史")
	@DeleteMapping("/delete/{id}")
	public Result delete(@PathVariable String id) {
		userListenProcessService.deleteListenProcess(AuthContextHolder.getUserId(), id);
		return Result.ok();
	}

	/**
	 * 最近一次收听记录，供播放页不带参数进入时自动续播"上次听到"。
	 */
	@LoginVerification
	@Operation(summary = "获取最近一次收听记录")
	@GetMapping("/getLatelyTrack")
	public Result<UserListenProcess> getLatelyTrack() {
		Long userId = AuthContextHolder.getUserId();
		return Result.ok(userListenProcessService.getLatelyTrack(userId));
	}
}

