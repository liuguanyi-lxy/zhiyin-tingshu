package com.atguigu.tingshu.account.service.impl;

import com.atguigu.tingshu.account.mapper.RechargeInfoMapper;
import com.atguigu.tingshu.account.mapper.UserAccountMapper;
import com.atguigu.tingshu.account.service.RechargeInfoService;
import com.atguigu.tingshu.account.service.UserAccountService;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.model.account.RechargeInfo;
import com.atguigu.tingshu.model.account.UserAccount;
import com.atguigu.tingshu.vo.account.RechargeInfoVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@SuppressWarnings({"all"})
public class RechargeInfoServiceImpl extends ServiceImpl<RechargeInfoMapper, RechargeInfo> implements RechargeInfoService {
	@Autowired
	private UserAccountService userAccountService;
	@Autowired
	private UserAccountMapper userAccountMapper;
	//根据充值记录编号修改充值记录
	@Override
	public void rechargePaySuccess(String orderNo) {
		// 获取到充值信息对象
		RechargeInfo rechargeInfo = this.getRechargeInfo(orderNo);
		Assert.notNull(rechargeInfo, "充值记录不存在");

		//【修复#5】条件更新实现幂等：仅"未支付→已支付"更新成功（受影响行数>0）才入账，
		// 重复消费的 MQ 消息或并发回调在此处被挡住，避免重复加钱。
		// 原实现"先查状态再 updateById"是 check-then-act，并发下会双重入账。
		int rows = this.baseMapper.update(null, new LambdaUpdateWrapper<RechargeInfo>()
				.eq(RechargeInfo::getOrderNo, orderNo)
				.eq(RechargeInfo::getRechargeStatus, SystemConstant.ORDER_STATUS_UNPAID)
				.set(RechargeInfo::getRechargeStatus, SystemConstant.ORDER_STATUS_PAID));
		if (rows == 0) {
			return;
		}

		//【修复#5】账户金额改为数据库原子累加（total_amount = total_amount + n），
		// 原实现"getOne → 内存加 → update"在并发下会丢失更新。
		int accountRows = userAccountMapper.incrTotalAndAvailable(rechargeInfo.getRechargeAmount(), rechargeInfo.getUserId());
		Assert.isTrue(accountRows > 0, "用户账户不存在");
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
