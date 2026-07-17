package com.atguigu.tingshu.account.api;

import com.atguigu.tingshu.account.service.UserAccountService;
import com.atguigu.tingshu.common.login.LoginVerification;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.vo.account.AccountLockVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@Tag(name = "用户账户管理")
@RestController
@RequestMapping("api/account/userAccount")
@SuppressWarnings({"all"})
public class UserAccountApiController {

	@Autowired
	private UserAccountService userAccountService;
	/**
	 * 获取账户可用余额
	 * @return
	 */
	@LoginVerification
	@Operation(summary = "获取账号可用金额")
	@GetMapping("getAvailableAmount")
	public Result<BigDecimal> getAvailableAmount() {
		// 调用服务层方法
		return Result.ok(userAccountService.getAvailableAmount(AuthContextHolder.getUserId()));
	}
	//,并且添加对应的购买记录
	@Operation(summary = ",并且添加对应的购买记录")
	@PostMapping("checkAndDeduct")
	Result checkAndDeduct(@RequestBody AccountLockVo accountDeductVo) {
		userAccountService.checkAndDeduct(accountDeductVo);
		return Result.ok();
	}
}

