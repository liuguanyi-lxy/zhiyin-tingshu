package com.atguigu.tingshu.account.service.impl;

import com.atguigu.tingshu.account.mapper.UserAccountDetailMapper;
import com.atguigu.tingshu.account.mapper.UserAccountMapper;
import com.atguigu.tingshu.account.service.UserAccountService;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import com.atguigu.tingshu.model.account.UserAccount;
import com.atguigu.tingshu.model.account.UserAccountDetail;
import com.atguigu.tingshu.vo.account.AccountLockVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

@Slf4j
@Service
@SuppressWarnings({"all"})
public class UserAccountServiceImpl extends ServiceImpl<UserAccountMapper, UserAccount> implements UserAccountService {

	@Autowired
	private UserAccountMapper userAccountMapper;
	@Autowired
	private UserAccountDetailMapper userAccountDetailMapper;
    /** 同一用户的扣款先锁账户，再检查消费流水，防止并发重试重复扣款。 */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Result checkAndDeduct(AccountLockVo request) {
        if (request == null || request.getUserId() == null || request.getUserId() <= 0
                || !StringUtils.hasText(request.getOrderNo())
                || request.getOrderNo().length() > 64
                || request.getAmount() == null || request.getAmount().signum() <= 0
                || request.getAmount().stripTrailingZeros().scale() > 2) {
            throw new GuiguException(ResultCodeEnum.ARGUMENT_VALID_ERROR);
        }

        UserAccount account = userAccountMapper.selectForDeduction(request.getUserId());
        if (account == null) {
            throw new GuiguException(ResultCodeEnum.ACCOUNT_ERROR);
        }
        // FOR UPDATE 使用当前读，避免事务快照遗漏前一次已提交的消费流水。
        UserAccountDetail previous = userAccountDetailMapper.selectOne(
                new LambdaQueryWrapper<UserAccountDetail>()
                        .eq(UserAccountDetail::getUserId, request.getUserId())
                        .eq(UserAccountDetail::getOrderNo, request.getOrderNo())
                        .eq(UserAccountDetail::getTradeType, SystemConstant.ACCOUNT_TRADE_TYPE_MINUS)
                        .last("FOR UPDATE"));
        if (previous != null) {
            if (previous.getAmount() == null || previous.getAmount().compareTo(request.getAmount()) != 0) {
                throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST);
            }
            return Result.ok();
        }

        int count = userAccountMapper.checkAndDeduct(request.getAmount(), request.getUserId());
        if (count == 0) {
            return Result.build(null, ResultCodeEnum.ACCOUNT_LESS);
        }
        if (count != 1) {
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }
        UserAccountDetail detail = new UserAccountDetail();
        detail.setUserId(request.getUserId());
        detail.setTitle(request.getContent());
        detail.setTradeType(SystemConstant.ACCOUNT_TRADE_TYPE_MINUS);
        detail.setAmount(request.getAmount());
        detail.setOrderNo(request.getOrderNo());
        if (userAccountDetailMapper.insert(detail) != 1) {
            throw new GuiguException(ResultCodeEnum.SERVICE_ERROR);
        }
        return Result.ok();
    }

	@Override
	public BigDecimal getAvailableAmount(Long userId) {
		// 根据用户Id 获取到用户余额对象
		UserAccount userAccount = this.getUserAccountByUserId(userId);
		return userAccount.getAvailableAmount();
	}

	public UserAccount getUserAccountByUserId(Long userId) {
		return this.getOne(new LambdaQueryWrapper<UserAccount>()
				.eq(UserAccount::getUserId, userId));
	}
    //添加用户账户信息
	@Override
	public void addUserAccount(Long userId) {
		// user_account
		UserAccount userAccount = new UserAccount();
		userAccount.setUserId(userId);
		userAccountMapper.insert(userAccount);
	}
}
