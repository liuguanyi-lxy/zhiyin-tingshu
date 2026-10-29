package com.atguigu.tingshu.account.api;

import com.atguigu.tingshu.account.service.UserAccountService;
import com.atguigu.tingshu.account.security.AccountInternalAuth;
import com.atguigu.tingshu.common.constant.AccountInternalConstant;
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
	@Autowired
	private AccountInternalAuth accountInternalAuth;
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

	//检查账户余额，充足则扣减余额并且添加对应的购买记录
	@Operation(summary = "检查账户余额，充足则扣减余额并且添加对应的购买记录")
	@PostMapping("checkAndDeduct")
	public Result checkAndDeduct(@RequestBody AccountLockVo accountDeductVo,
			@RequestHeader(value = AccountInternalConstant.HEADER, required = false) String internalToken) {
		accountInternalAuth.requireTrustedCaller(internalToken);
		Result result = userAccountService.checkAndDeduct(accountDeductVo);
		return result;
	}
	//根据用户ID在account数据库初始化账户
	@Operation(summary = "根据用户ID在account数据库初始化账户")
	@PostMapping("addUserAccount/{userId}")
	public Result addUserAccount(@PathVariable Long userId) {
		if(userId != null) {
			userAccountService.addUserAccount(userId);
		}
		return Result.ok();
	}
}

