package com.atguigu.tingshu.account.service;

import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.account.UserAccount;
import com.atguigu.tingshu.vo.account.AccountLockVo;
import com.atguigu.tingshu.vo.account.AccountRecordVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

    /** 当前用户已消费的 1204 流水，不包含充值、锁定或解锁。 */
    Page<AccountRecordVo> findUserConsumePage(Long userId, long page, long limit);

    /** 当前用户 0902 已支付充值单，不包含未支付或已取消充值。 */
    Page<AccountRecordVo> findUserRechargePage(Long userId, long page, long limit);
}