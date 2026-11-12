package com.atguigu.tingshu.payment.service;

import com.wechat.pay.java.service.payments.model.Transaction;

import java.util.Map;

public interface WxPayService {
    //调用微信支付接口
    Map createJsapi(String paymentType, String orderNo, Long userId);
    // Native 扫码支付，金额和可付状态必须从当前用户的服务端订单/充值记录获取。
    Map<String, String> createNative(String paymentType, String orderNo, Long userId);
    // 保留旧签名供内部调用兼容；外部 Controller 必须使用带当前用户的重载。
    Transaction queryPayStatus(String orderNo);
    Transaction queryPayStatus(String orderNo, Long userId);
}
