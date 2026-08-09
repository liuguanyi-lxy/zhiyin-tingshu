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

import java.math.BigDecimal;

@Slf4j
@Service
@SuppressWarnings({"all"})
public class UserAccountServiceImpl extends ServiceImpl<UserAccountMapper, UserAccount> implements UserAccountService {

	@Autowired
	private UserAccountMapper userAccountMapper;
	@Autowired
	private UserAccountDetailMapper userAccountDetailMapper;
	//检查和扣减余额,并且添加对应的购买记录
//	@Override
//	public void checkAndDeduct(AccountLockVo accountDeductVo) {
//		//只有账号余额大于等于要扣减的金额才能扣减金额，否则就不会更新账户的total_amount和available_amount
//		// ,根据count即影响的行数判断是否扣减成功
//		int count = userAccountMapper.checkAndDeduct(accountDeductVo.getAmount(), accountDeductVo.getUserId());
//		//扣减不成功就抛出异常，账户余额不足给前端
//		if(count == 0) {
//			throw new GuiguException(ResultCodeEnum.ACCOUNT_LESS);
//		}
//		//添加账户记录到账户详情表
//		UserAccountDetail userAccountDetail=new UserAccountDetail();
//		userAccountDetail.setUserId(accountDeductVo.getUserId());
//		userAccountDetail.setTitle(accountDeductVo.getContent());
//		userAccountDetail.setTradeType(SystemConstant.ACCOUNT_TRADE_TYPE_MINUS);
//		userAccountDetail.setAmount(accountDeductVo.getAmount());
//		userAccountDetail.setOrderNo(accountDeductVo.getOrderNo());
//		userAccountDetailMapper.insert(userAccountDetail);
//	}
	//检查并扣减余额，使用数据库原子SQL实现，条件更新保证并发扣减正确且余额不足时影响行数为0
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Result checkAndDeduct(AccountLockVo accountDeductVo) {
       //调用原子SQL，由数据库在当前行上执行 total_amount-amount、available_amount-amount
       //MySQL对单行UPDATE加行锁，并发扣款串行执行，不会出现先读后改导致的丢失更新
       int count = userAccountMapper.checkAndDeduct(accountDeductVo.getAmount(), accountDeductVo.getUserId());
       //余额不足,设置对应的返回结果
       if(count == 0) {
           log.error("抛出余额不足异常");
         return Result.fail().code(ResultCodeEnum.ACCOUNT_LESS.getCode()).message(ResultCodeEnum.ACCOUNT_LESS.getMessage());
       }
        log.info("账户余额充足继续执行，添加账户消费记录");
       //添加账户记录到账户详情表
       UserAccountDetail userAccountDetail=new UserAccountDetail();
       userAccountDetail.setUserId(accountDeductVo.getUserId());
       userAccountDetail.setTitle(accountDeductVo.getContent());
       userAccountDetail.setTradeType(SystemConstant.ACCOUNT_TRADE_TYPE_MINUS);
       userAccountDetail.setAmount(accountDeductVo.getAmount());
       userAccountDetail.setOrderNo(accountDeductVo.getOrderNo());
       userAccountDetailMapper.insert(userAccountDetail);
       return Result.ok();
    }
	//获取用户可用余额
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
