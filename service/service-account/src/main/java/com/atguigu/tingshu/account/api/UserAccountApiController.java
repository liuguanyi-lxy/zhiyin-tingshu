package com.atguigu.tingshu.account.api;

import com.atguigu.tingshu.account.security.AccountInternalAuth;
import com.atguigu.tingshu.account.service.UserAccountService;
import com.atguigu.tingshu.common.constant.AccountInternalConstant;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.login.LoginVerification;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.vo.account.AccountLockVo;
import com.atguigu.tingshu.vo.account.AccountRecordVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

    @LoginVerification
    @Operation(summary = "当前用户消费记录分页")
    @GetMapping("findUserConsumePage/{page}/{limit}")
    public Result<Page<AccountRecordVo>> findUserConsumePage(@PathVariable("page") long page, @PathVariable("limit") long limit) {
        return Result.ok(userAccountService.findUserConsumePage(AuthContextHolder.getUserId(), page, limit));
    }

    @LoginVerification
    @Operation(summary = "当前用户已支付充值记录分页")
    @GetMapping("findUserRechargePage/{page}/{limit}")
    public Result<Page<AccountRecordVo>> findUserRechargePage(@PathVariable("page") long page, @PathVariable("limit") long limit) {
        return Result.ok(userAccountService.findUserRechargePage(AuthContextHolder.getUserId(), page, limit));
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
	public Result addUserAccount(@PathVariable Long userId,
			@RequestHeader(value = AccountInternalConstant.HEADER, required = false) String internalToken) {
		accountInternalAuth.requireTrustedCaller(internalToken);
		if (userId == null || userId <= 0) {
			throw new GuiguException(ResultCodeEnum.ARGUMENT_VALID_ERROR);
		}
		userAccountService.addUserAccount(userId);
		return Result.ok();
	}
}

