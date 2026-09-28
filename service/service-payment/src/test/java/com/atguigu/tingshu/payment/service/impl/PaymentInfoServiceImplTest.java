package com.atguigu.tingshu.payment.service.impl;

import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import com.atguigu.tingshu.common.rabbit.service.RabbitService;
import com.atguigu.tingshu.model.payment.PaymentInfo;
import com.atguigu.tingshu.order.client.OrderInfoFeignClient;
import com.atguigu.tingshu.account.client.RechargeInfoFeignClient;
import com.atguigu.tingshu.payment.mapper.PaymentInfoMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.payments.model.TransactionAmount;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 支付回调核心逻辑单元测试（纯 Mockito，不依赖数据库/Redis/MQ）。
 * 覆盖【修复#5】的三类行为：金额校验、条件更新幂等、MQ 路由分发。
 */
@ExtendWith(MockitoExtension.class)
class PaymentInfoServiceImplTest {

    @Mock
    private PaymentInfoMapper paymentInfoMapper;
    @Mock
    private OrderInfoFeignClient orderInfoFeignClient;
    @Mock
    private RechargeInfoFeignClient rechargeInfoFeignClient;
    @Mock
    private RabbitService rabbitService;
    @InjectMocks
    private PaymentInfoServiceImpl service;

    @BeforeAll
    static void initTableMeta() {
        // LambdaUpdateWrapper 解析实体列名需要 MyBatis-Plus 的 TableInfo 元数据
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), PaymentInfo.class);
    }

    @BeforeEach
    void injectBaseMapper() throws Exception {
        // ServiceImpl 继承的 baseMapper 字段与显式声明的 paymentInfoMapper 同类型，统一注入同一个 mock
        Field baseMapper = com.baomidou.mybatisplus.extension.service.impl.ServiceImpl.class.getDeclaredField("baseMapper");
        baseMapper.setAccessible(true);
        baseMapper.set(service, paymentInfoMapper);
    }

    private PaymentInfo unpaidPaymentInfo(String paymentType, String amountYuan) {
        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setOrderNo("ORDER123");
        paymentInfo.setPaymentType(paymentType);
        paymentInfo.setPaymentStatus(SystemConstant.PAYMENT_STATUS_UNPAID);
        paymentInfo.setAmount(new BigDecimal(amountYuan));
        return paymentInfo;
    }

    private Transaction transactionWithTotalFen(int totalFen) {
        Transaction transaction = new Transaction();
        transaction.setOutTradeNo("ORDER123");
        transaction.setTransactionId("WX-TXN-001");
        TransactionAmount amount = new TransactionAmount();
        amount.setTotal(totalFen);
        transaction.setAmount(amount);
        return transaction;
    }

    @Test
    void orderPaymentWithMatchingAmount_sendsOrderPaySuccessMq() {
        when(paymentInfoMapper.selectOne(any())).thenReturn(unpaidPaymentInfo(SystemConstant.PAYMENT_TYPE_ORDER, "19.90"));
        when(paymentInfoMapper.update(isNull(), any())).thenReturn(1);

        service.updatePaymentStatus(transactionWithTotalFen(1990));

        verify(rabbitService).sendMessage(MqConst.EXCHANGE_ORDER, MqConst.ROUTING_ORDER_PAY_SUCCESS, "ORDER123");
    }

    @Test
    void rechargePaymentWithMatchingAmount_sendsRechargePaySuccessMq() {
        when(paymentInfoMapper.selectOne(any())).thenReturn(unpaidPaymentInfo(SystemConstant.PAYMENT_TYPE_RECHARGE, "50.00"));
        when(paymentInfoMapper.update(isNull(), any())).thenReturn(1);

        service.updatePaymentStatus(transactionWithTotalFen(5000));

        verify(rabbitService).sendMessage(MqConst.EXCHANGE_ORDER, MqConst.ROUTING_RECHARGE_PAY_SUCCESS, "ORDER123");
    }

    @Test
    void amountMismatch_throwsAndNeverSendsMq() {
        when(paymentInfoMapper.selectOne(any())).thenReturn(unpaidPaymentInfo(SystemConstant.PAYMENT_TYPE_ORDER, "19.90"));

        // 本地订单 19.90 元 = 1990 分，微信回调只付了 1 分 → 必须拒绝入账
        assertThrows(GuiguException.class, () -> service.updatePaymentStatus(transactionWithTotalFen(1)));

        verify(rabbitService, never()).sendMessage(anyString(), anyString(), anyString());
        verify(paymentInfoMapper, never()).update(isNull(), any());
    }

    @Test
    void duplicateCallback_skipsMqAndKeepsIdempotent() {
        when(paymentInfoMapper.selectOne(any())).thenReturn(unpaidPaymentInfo(SystemConstant.PAYMENT_TYPE_ORDER, "19.90"));
        // 条件更新受影响行数 = 0：说明记录已是"已支付"，本次为重复回调
        when(paymentInfoMapper.update(isNull(), any())).thenReturn(0);

        service.updatePaymentStatus(transactionWithTotalFen(1990));

        verify(rabbitService, never()).sendMessage(anyString(), anyString(), anyString());
    }

    @Test
    void paymentRecordMissing_throws() {
        when(paymentInfoMapper.selectOne(any())).thenReturn(null);

        assertThrows(Exception.class, () -> service.updatePaymentStatus(transactionWithTotalFen(1990)));

        verify(rabbitService, never()).sendMessage(anyString(), anyString(), anyString());
    }

    @Test
    void savePaymentInfo_existingRecord_returnsDirectlyWithoutInsert() {
        PaymentInfo existing = unpaidPaymentInfo(SystemConstant.PAYMENT_TYPE_ORDER, "19.90");
        when(paymentInfoMapper.selectOne(any())).thenReturn(existing);

        PaymentInfo result = service.savePaymentInfo(SystemConstant.PAYMENT_TYPE_ORDER, "ORDER123");

        assertEquals(existing, result);
        verify(paymentInfoMapper, never()).insert(any(PaymentInfo.class));
        verify(orderInfoFeignClient, never()).getOrderInfo(anyString());
    }
}
