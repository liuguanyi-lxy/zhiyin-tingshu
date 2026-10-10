package com.atguigu.tingshu.user.api;

import com.atguigu.tingshu.common.login.LoginVerification;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.model.user.UserInfo;
import com.atguigu.tingshu.user.service.UserCollectService;
import com.atguigu.tingshu.user.service.UserInfoService;
import com.atguigu.tingshu.user.service.UserSubscribeService;
import com.atguigu.tingshu.vo.user.UserCollectVo;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import com.atguigu.tingshu.vo.user.UserPaidRecordVo;
import com.atguigu.tingshu.vo.user.UserSubscribeVo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "用户管理接口")
@RestController
@Slf4j
@RequestMapping("api/user/userInfo")
@SuppressWarnings({"all"})
public class UserInfoApiController {

	@Autowired
	private UserInfoService userInfoService;

	@Autowired
	private UserSubscribeService userSubscribeService;

	@Autowired
	private UserCollectService userCollectService;
	/**
	 * 远程调用接口:根据用户Id获取用户信息
	 * 【修复#UserCdc】原实现与 WxLoginApiController 各自拼装 UserInfoVo，逻辑重复且绕过了
	 * getUserInfoVoByUserId 上的 @RedisCache 缓存；现统一收敛到服务层同一方法，
	 * 命中 "userInfoVo:[userId]" 缓存，CDC 变更时同步失效
	 * @param userId
	 * @return
	 */
	@Operation(summary = "根据用户id获取用户信息")
	@GetMapping("getUserInfoVo/{userId}")
	public Result<UserInfoVo> getUserInfoVo(@PathVariable Long userId) {
		return Result.ok(userInfoService.getUserInfoVoByUserId(userId));
	}
	//根据用户id查询用户是否购买专辑或者声音
	@LoginVerification(required = false)
	@Operation(summary = "判断用户是否购买声音列表,key是声音ID，value是声音是否被购买的标志")
	@PostMapping("userIsPaidTrack/{albumId}")
	public Result<Map<Long, Integer>> userIsPaidTrack(@PathVariable Long albumId,
													  @RequestBody List<Long> trackIdList) {
		// 获取用户Id
		Long userId = AuthContextHolder.getUserId();
		// 调用服务层方法
		Map<Long, Integer> map = userInfoService.userIsPaidTrack(userId, albumId, trackIdList);
		// 返回map 集合数据
		return Result.ok(map);
	}
	//用户是否购买指定ID的专辑
	@LoginVerification
	@Operation(summary = "根据专辑ID和被FeignInterceptor拦截后传入的用户ID判断用户是否购买专辑")
	@GetMapping("isPaidAlbum/{albumId}")
	public Result<Boolean> isPaidAlbum(@PathVariable("albumId") Long albumId) {
		Long userId = AuthContextHolder.getUserId();
		Boolean isPaid = userInfoService.isPaidAlbum(albumId,userId);
		return Result.ok(isPaid);
	}
	//根据专辑id和用户ID获取用户支付过的声音id列表
	@LoginVerification
	@Operation(summary = "根据专辑id和用户ID获取用户支付过的声音id列表")
	@GetMapping("findUserPaidTrackList/{albumId}")
	public Result<List<Long>> findUserIsPaidTrack(@PathVariable Long albumId){
		// 获取用户Id
		Long userId = AuthContextHolder.getUserId();
		// 根据用户Id 与 专辑Id 获取到已购买的声音Id 集合列表
		List<Long> trackIdList = this.userInfoService.findPaidUserTrackList(userId,albumId);
		// 返回声音Id 集合数据
		return Result.ok(trackIdList);
	}
	/**
	 * 订单生成时添加用户购买记录
	 * @param userPaidRecordVo
	 * @return
	 */
	@Operation(summary = "订单生成时添加用户购买记录")
	@PostMapping("/savePaidRecord")
	public Result savePaidRecord(@RequestBody UserPaidRecordVo userPaidRecordVo){
		// 调用服务层方法.
		userInfoService.savePaidRecord(userPaidRecordVo);
		// 默认返回
		return Result.ok();
	}
	@Operation(summary = "更新用户Vip到期时用户的状态")
	@GetMapping("updateVipExpireStatus")
	public Result updateVipExpireStatus() {
		userInfoService.updateVipExpireStatus();
		return Result.ok();
	}

	/**
	 * 订阅/取消订阅专辑（切换）：同一接口来回切换，返回切换后的订阅状态。
	 * 前端专辑详情页的"订阅"按钮调用。
	 */
	@LoginVerification
	@Operation(summary = "订阅/取消订阅专辑（切换），返回切换后的状态")
	@GetMapping("subscribe/{albumId}")
	public Result<Boolean> subscribeAlbum(@PathVariable Long albumId) {
		Long userId = AuthContextHolder.getUserId();
		return Result.ok(userSubscribeService.subscribeAlbum(userId, albumId));
	}

	/**
	 * 查询是否已订阅指定专辑。详情页可匿名访问，未登录返回 false（该路径在网关白名单，见 AuthGlobalFilter）。
	 */
	@LoginVerification(required = false)
	@Operation(summary = "查询当前用户是否已订阅专辑")
	@GetMapping("isSubscribe/{albumId}")
	public Result<Boolean> isSubscribeAlbum(@PathVariable Long albumId) {
		return Result.ok(userSubscribeService.isSubscribeAlbum(AuthContextHolder.getUserId(), albumId));
	}

	/**
	 * 收藏/取消收藏声音（切换）：同一接口来回切换，返回切换后的收藏状态。
	 * 前端播放页的"收藏"按钮调用。
	 */
	@LoginVerification
	@Operation(summary = "收藏/取消收藏声音（切换），返回切换后的状态")
	@GetMapping("collect/{trackId}")
	public Result<Boolean> collectTrack(@PathVariable Long trackId) {
		Long userId = AuthContextHolder.getUserId();
		return Result.ok(userCollectService.collectTrack(userId, trackId));
	}

	/**
	 * 查询是否已收藏指定声音。播放页可匿名访问，未登录返回 false（该路径在网关白名单，见 AuthGlobalFilter）。
	 */
	@LoginVerification(required = false)
	@Operation(summary = "查询当前用户是否已收藏声音")
	@GetMapping("isCollect/{trackId}")
	public Result<Boolean> isCollectTrack(@PathVariable Long trackId) {
		return Result.ok(userCollectService.isCollectTrack(AuthContextHolder.getUserId(), trackId));
	}

	/**
	 * 分页查询"我的订阅"（MongoDB），供前端"我的"页订阅 Tab 使用。
	 */
	@LoginVerification
	@Operation(summary = "分页查询我的订阅")
	@GetMapping("findUserSubscribePage/{page}/{limit}")
	public Result<IPage<UserSubscribeVo>> findUserSubscribePage(@PathVariable Long page, @PathVariable Long limit) {
		Long userId = AuthContextHolder.getUserId();
		return Result.ok(userSubscribeService.findUserSubscribePage(page, limit, userId));
	}

	/**
	 * 分页查询"我的收藏"（MongoDB），供前端"我的"页收藏 Tab 使用。
	 */
	@LoginVerification
	@Operation(summary = "分页查询我的收藏")
	@GetMapping("findUserCollectPage/{page}/{limit}")
	public Result<IPage<UserCollectVo>> findUserCollectPage(@PathVariable Long page, @PathVariable Long limit) {
		Long userId = AuthContextHolder.getUserId();
		return Result.ok(userCollectService.findUserCollectPage(page, limit, userId));
	}
}

