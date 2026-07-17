package com.atguigu.tingshu.account.service.impl;

import com.atguigu.tingshu.account.mapper.UserAccountDetailMapper;
import com.atguigu.tingshu.account.mapper.UserAccountMapper;
import com.atguigu.tingshu.account.service.UserAccountService;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import com.atguigu.tingshu.model.account.UserAccount;
import com.atguigu.tingshu.model.account.UserAccountDetail;
import com.atguigu.tingshu.vo.account.AccountLockVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
	//检查并扣减余额，使用mybatisplus实现
    @Override
    public void checkAndDeduct(AccountLockVo accountDeductVo) {
       //编写sql语句实现 检查并扣减余额
//     int count = userAccountMapper
//           .checkAndDeduct(accountDeductVo.getAmount(), accountDeductVo.getUserId());

//     UPDATE user_account ua
//     SET ua.available_amount=ua.available_amount-20,
//           ua.total_amount=ua.total_amount-20
//     WHERE ua.user_id=35
//        and ua.available_amount>=20
       //不编写语句实现 检查并扣减余额
       LambdaQueryWrapper<UserAccount> wrapper = new LambdaQueryWrapper<>();
       //WHERE ua.user_id=35
       wrapper.eq(UserAccount::getUserId,accountDeductVo.getUserId());
       //and ua.available_amount>=20
       wrapper.ge(UserAccount::getAvailableAmount,accountDeductVo.getAmount());

       //查询userId账户原始金额，减去支付金额，把最终最终金额设置到userAccount对象里面
       LambdaQueryWrapper<UserAccount> wrapperOldUserAccount = new LambdaQueryWrapper<>();
       wrapperOldUserAccount.eq(UserAccount::getUserId,accountDeductVo.getUserId());
       UserAccount userAccount = userAccountMapper.selectOne(wrapperOldUserAccount);

       userAccount.setAvailableAmount(userAccount.getAvailableAmount().subtract(accountDeductVo.getAmount()));
       userAccount.setTotalAmount(userAccount.getTotalAmount().subtract(accountDeductVo.getAmount()));

       //调用update方法，根据条件进行检查和扣减余额,实体类应该是被修改对应属性值的实体类，条件是修改条件
       int count = userAccountMapper.update(userAccount, wrapper);
       //余额不足
       if(count == 0) {
          throw new GuiguException(ResultCodeEnum.ACCOUNT_LESS);
       }

       //添加账户记录到账户详情表
       UserAccountDetail userAccountDetail=new UserAccountDetail();
       userAccountDetail.setUserId(accountDeductVo.getUserId());
       userAccountDetail.setTitle(accountDeductVo.getContent());
       userAccountDetail.setTradeType(SystemConstant.ACCOUNT_TRADE_TYPE_MINUS);
       userAccountDetail.setAmount(accountDeductVo.getAmount());
       userAccountDetail.setOrderNo(accountDeductVo.getOrderNo());
       userAccountDetailMapper.insert(userAccountDetail);
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

	@Override
	public void addUserAccount(Long userId) {
		// user_account
		UserAccount userAccount = new UserAccount();
		userAccount.setUserId(userId);
		userAccountMapper.insert(userAccount);
	}
}
