package com.atguigu.tingshu.account.service;

import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.account.UserAccount;
import com.atguigu.tingshu.vo.account.AccountLockVo;
import com.baomidou.mybatisplus.extension.service.IService;

import java.math.BigDecimal;

public interface UserAccountService extends IService<UserAccount> {

    /**
     * 添加账户信息
     *
     * @param userId
     */
    void addUserAccount(Long userId);
    //获取用户可用余额
    BigDecimal getAvailableAmount(Long userId);
    //检查和扣减余额
    Result checkAndDeduct(AccountLockVo accountDeductVo);
}