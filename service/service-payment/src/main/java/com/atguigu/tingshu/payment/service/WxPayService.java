package com.atguigu.tingshu.payment.service;

import com.wechat.pay.java.service.payments.model.Transaction;

import java.util.Map;

public interface WxPayService {
    //调用微信支付接口
    Map createJsapi(String paymentType, String orderNo, Long userId);
    //查询订单状态
    Transaction queryPayStatus(String orderNo);
}
