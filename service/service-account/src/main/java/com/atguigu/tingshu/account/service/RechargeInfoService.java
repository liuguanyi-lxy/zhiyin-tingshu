package com.atguigu.tingshu.account.service;

import com.atguigu.tingshu.model.account.RechargeInfo;
import com.atguigu.tingshu.vo.account.RechargeInfoVo;
import com.baomidou.mybatisplus.extension.service.IService;

public interface RechargeInfoService extends IService<RechargeInfo> {
    //在recharge_info表中添加充值记录
    String submitRecharge(RechargeInfoVo rechargeInfoVo, Long userId);
    //根据充值单号查询充值记录
    RechargeInfo getRechargeInfo(String rechargeNo);
    //充值成功后修改recharge_info表中的状态
    void rechargePaySuccess(String orderNo);
}
