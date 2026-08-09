package com.atguigu.tingshu.payment.service;

import com.atguigu.tingshu.model.payment.PaymentInfo;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wechat.pay.java.service.payments.model.Transaction;

public interface PaymentInfoService extends IService<PaymentInfo> {
    // 保存支付信息
    PaymentInfo savePaymentInfo(String paymentType, String orderNo);
    // 更新支付状态
    void updatePaymentStatus(Transaction transaction);
}
