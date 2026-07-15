package com.atguigu.tingshu.user.api;

import com.atguigu.tingshu.common.login.LoginVerification;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.model.user.UserInfo;
import com.atguigu.tingshu.user.service.UserInfoService;
import com.atguigu.tingshu.vo.user.UserInfoVo;
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
	/**
	 * 远程调用接口:根据用户Id获取用户信息
	 * @param userId
	 * @return
	 */
	@Operation(summary = "根据用户id获取用户信息")
	@GetMapping("getUserInfoVo/{userId}")
	public Result<UserInfoVo> getUserInfoVo(@PathVariable Long userId) {

		UserInfo userInfo = userInfoService.getById(userId);
		log.info("userid:{}",userId);
		Assert.notNull(userInfo, String.valueOf(ResultCodeEnum.DATA_ERROR));
		// 创建UserInfoVo 对象
		UserInfoVo userInfoVo = new UserInfoVo();
		// 属性拷贝
		BeanUtils.copyProperties(userInfo,userInfoVo);

		return Result.ok(userInfoVo);
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
}

