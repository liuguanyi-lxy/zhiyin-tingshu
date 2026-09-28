package com.atguigu.tingshu.payment.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.tingshu.account.client.RechargeInfoFeignClient;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import com.atguigu.tingshu.common.rabbit.service.RabbitService;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import com.atguigu.tingshu.model.account.RechargeInfo;
import com.atguigu.tingshu.model.order.OrderInfo;
import com.atguigu.tingshu.model.payment.PaymentInfo;
import com.atguigu.tingshu.order.client.OrderInfoFeignClient;
import com.atguigu.tingshu.payment.mapper.PaymentInfoMapper;
import com.atguigu.tingshu.payment.service.PaymentInfoService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wechat.pay.java.service.payments.model.Transaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.Date;

@Slf4j
@Service
@SuppressWarnings({"all"})
public class PaymentInfoServiceImpl extends ServiceImpl<PaymentInfoMapper, PaymentInfo> implements PaymentInfoService {
    //向payment数据库保存支付记录
    @Autowired
    private OrderInfoFeignClient orderInfoFeignClient;
    @Autowired
    private RechargeInfoFeignClient rechargeInfoFeignClient;

    @Autowired
    private PaymentInfoMapper paymentInfoMapper;
    @Autowired
    private RabbitService rabbitService;
    //微信支付成功后更新支付记录状态
    //调用方法更新订单或者支付记录和购买记录
    @Override
    public void updatePaymentStatus(Transaction transaction) {
        //获取订单编号
        String orderNo = transaction.getOutTradeNo();
        PaymentInfo paymentInfo = this.getOne(new LambdaQueryWrapper<PaymentInfo>()
                .eq(PaymentInfo::getOrderNo, orderNo));
        Assert.notNull(paymentInfo, "支付记录不存在: " + orderNo);

        //【修复#5】金额校验：微信回调的订单总金额（分）必须与本地支付记录一致，
        // 防止"1分钱支付整单"类金额篡改；不一致抛异常 → 回调端点返回 FAIL
        Assert.notNull(transaction.getAmount(), "回调报文缺少金额信息");
        Long paidTotalFen = transaction.getAmount().getTotal() == null ? null : transaction.getAmount().getTotal().longValue();
        Long expectTotalFen = paymentInfo.getAmount().movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValue();
        if (!expectTotalFen.equals(paidTotalFen)) {
            log.error("[支付回调] 金额不一致，拒绝入账。orderNo={}, 本地金额(分)={}, 微信回调金额(分)={}",
                    orderNo, expectTotalFen, paidTotalFen);
            throw new GuiguException(ResultCodeEnum.DATA_ERROR);
        }

        //【修复#5】条件更新实现幂等：仅"未支付→已支付"更新成功（受影响行数>0）才发 MQ，
        // 原实现"先查状态再 updateById"是 check-then-act，并发回调/重复消息会重复发 MQ 导致权益重复发放
        int rows = this.baseMapper.update(null, new LambdaUpdateWrapper<PaymentInfo>()
                .eq(PaymentInfo::getOrderNo, orderNo)
                .eq(PaymentInfo::getPaymentStatus, SystemConstant.PAYMENT_STATUS_UNPAID)
                .set(PaymentInfo::getPaymentStatus, SystemConstant.PAYMENT_STATUS_PAID)
                .set(PaymentInfo::getOutTradeNo, transaction.getTransactionId())
                .set(PaymentInfo::getCallbackTime, new Date())
                .set(PaymentInfo::getCallbackContent, JSON.toJSONString(transaction)));
        if (rows == 0) {
            log.info("[支付回调] 支付记录已处理，忽略重复回调: {}", orderNo);
            return;
        }

        //微信支付成功后发送mq消息orderNo，更新订单状态以及用户信息，或者更新账户的充值记录状态以及账户信息
        //1301-订单    1302-充值
        String routing =
                paymentInfo.getPaymentType().equals(SystemConstant.PAYMENT_TYPE_ORDER)
                        ? MqConst.ROUTING_ORDER_PAY_SUCCESS
                        : MqConst.ROUTING_RECHARGE_PAY_SUCCESS;
        //发送mq消息,指定交换机和路由key以及消息，异步更新订单状态
        rabbitService.sendMessage(MqConst.EXCHANGE_ORDER,
                routing,
                paymentInfo.getOrderNo());
    }

    //保存支付记录
    @Override
    public PaymentInfo savePaymentInfo(String paymentType, String orderNo) {
        //1 根据订单编号，查询支付记录表
        LambdaQueryWrapper<PaymentInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaymentInfo::getOrderNo,orderNo);
        PaymentInfo paymentInfo = paymentInfoMapper.selectOne(wrapper);

        //2 判断如果没有查到数据，第一次添加，如果查询到直接返回
        if(paymentInfo == null) {
            paymentInfo = new PaymentInfo();
            //判断支付种类paymentType ，购买订单  或者  充值
            //1301-订单
            if(paymentType.equals(SystemConstant.PAYMENT_TYPE_ORDER)) {
                //  远程调用获取到订单对象,根据订单记录的信息设置支付记录的信息
                Result<OrderInfo> orderInfoResult = orderInfoFeignClient.getOrderInfo(orderNo);
                Assert.notNull(orderInfoResult,"返回订单对象不能为空");
                OrderInfo orderInfo = orderInfoResult.getData();
                Assert.notNull(orderInfo,"返回订单对象不能为空");
                //支付金额
                paymentInfo.setAmount(orderInfo.getOrderAmount());
                //userId
                paymentInfo.setUserId(orderInfo.getUserId());
                paymentInfo.setContent(orderInfo.getOrderTitle());
            } else { //1302-充值
                //  查询到充值记录的信息用于填充支付记录的信息
                Result<RechargeInfo> rechargeInfoResult = rechargeInfoFeignClient.getRechargeInfo(orderNo);
                Assert.notNull(rechargeInfoResult,"返回充值对象不能不为空");
                RechargeInfo rechargeInfo = rechargeInfoResult.getData();
                Assert.notNull(rechargeInfo,"返回充值对象不能不为空");
                paymentInfo.setUserId(rechargeInfo.getUserId());
                paymentInfo.setContent("充值");
                paymentInfo.setAmount(rechargeInfo.getRechargeAmount());
            }
            paymentInfo.setPayWay(SystemConstant.ORDER_PAY_WAY_WEIXIN);
            paymentInfo.setPaymentType(paymentType);
            paymentInfo.setOrderNo(orderNo);
            //状态：未支付
            paymentInfo.setPaymentStatus(SystemConstant.PAYMENT_STATUS_UNPAID);
            paymentInfoMapper.insert(paymentInfo);
        }
        return paymentInfo;
    }
}
