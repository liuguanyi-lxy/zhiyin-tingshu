package com.atguigu.tingshu.account.service.impl;

import com.atguigu.tingshu.account.mapper.RechargeInfoMapper;
import com.atguigu.tingshu.account.service.RechargeInfoService;
import com.atguigu.tingshu.account.service.UserAccountService;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.model.account.RechargeInfo;
import com.atguigu.tingshu.model.account.UserAccount;
import com.atguigu.tingshu.vo.account.RechargeInfoVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@SuppressWarnings({"all"})
public class RechargeInfoServiceImpl extends ServiceImpl<RechargeInfoMapper, RechargeInfo> implements RechargeInfoService {
	@Autowired
	private UserAccountService userAccountService;
	//根据充值记录编号修改充值记录
	@Override
	public void rechargePaySuccess(String orderNo) {
		// 获取到充值信息对象
		RechargeInfo rechargeInfo = this.getRechargeInfo(orderNo);
		// 如果当前状态是已支付，则直接返回
		if (SystemConstant.ORDER_STATUS_PAID.equals(rechargeInfo.getRechargeStatus())) return;
		// 否则赋值为已支付状态
		rechargeInfo.setRechargeStatus(SystemConstant.ORDER_STATUS_PAID);
		// 更新数据
		this.updateById(rechargeInfo);

		//更新账户金额
		//获取充值金额
		BigDecimal rechargeAmount = rechargeInfo.getRechargeAmount();
		//获取账户原始金额
		Long userId = rechargeInfo.getUserId();
		LambdaQueryWrapper<UserAccount> wrapper = new LambdaQueryWrapper<>();
		wrapper.eq(UserAccount::getUserId, userId);

		UserAccount userAccount = userAccountService.getOne(wrapper);
		//调用
		userAccount.setTotalAmount(userAccount.getTotalAmount()
				.add(rechargeAmount));
		userAccount.setAvailableAmount(userAccount.getAvailableAmount()
				.add(rechargeAmount));

		LambdaQueryWrapper<UserAccount> wrapper1 = new LambdaQueryWrapper<>();
		wrapper1.eq(UserAccount::getUserId, userId);
		userAccountService.update(userAccount,wrapper1);
	}

	//根据充值订单编号查询充值记录
	@Override
	public RechargeInfo getRechargeInfo(String rechargeNo) {
		LambdaQueryWrapper<RechargeInfo> queryWrapper = new LambdaQueryWrapper<>();
		queryWrapper.eq(RechargeInfo::getOrderNo,rechargeNo);
		RechargeInfo rechargeInfo = this.getOne(queryWrapper);
		return rechargeInfo;
	}

	@Autowired
	private RechargeInfoMapper rechargeInfoMapper;
	//在recharge_info表中添加充值记录
	@Override
	public String submitRecharge(RechargeInfoVo rechargeInfoVo, Long userId) {
		// 创建对象
		RechargeInfo rechargeInfo = new RechargeInfo();
		// 赋值操作
		rechargeInfo.setUserId(userId);
		// 0901-未支付
		rechargeInfo.setRechargeStatus(SystemConstant.ORDER_STATUS_UNPAID);
		//充值金额
		rechargeInfo.setRechargeAmount(rechargeInfoVo.getAmount());
		//充值方式 微信
		rechargeInfo.setPayWay(rechargeInfoVo.getPayWay());
		//充值订单编号
		rechargeInfo.setOrderNo(UUID.randomUUID().toString().replaceAll("-",""));
		// 插入数据库,添加充值记录
		rechargeInfoMapper.insert(rechargeInfo);
		// 返回充值记录编号
		return rechargeInfo.getOrderNo();
	}
}
