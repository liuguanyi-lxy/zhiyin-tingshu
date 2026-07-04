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
}

