package com.atguigu.tingshu.payment.service.impl;

import com.atguigu.tingshu.account.client.RechargeInfoFeignClient;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.account.RechargeInfo;
import com.atguigu.tingshu.model.order.OrderInfo;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.atguigu.tingshu.model.payment.PaymentInfo;
import com.atguigu.tingshu.order.client.OrderInfoFeignClient;
import com.atguigu.tingshu.payment.config.WxPayV3Config;
import com.atguigu.tingshu.payment.service.PaymentInfoService;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 只 mock 微信 SDK 边界及远端/Redis；不连接微信、数据库或 Redis。 */
@ExtendWith(MockitoExtension.class)
class WxPayServiceImplTest {
    private static final String ORDER_NO = "order123";
    private static final Long USER_ID = 7L;
    private static final Instant NOW = Instant.parse("2026-11-12T02:00:00Z");
    @Mock private OrderInfoFeignClient orderClient;
    @Mock private RechargeInfoFeignClient rechargeClient;
    @Mock private PaymentInfoService paymentInfoService;
    @Mock private RedissonClient redissonClient;
    @Mock private RLock lock;
    @Mock private WxPayServiceImpl.WechatPayClient wechatClient;
    private WxPayServiceImpl service;
    private WxPayV3Config config;

    @BeforeEach
    void setUp() {
        service = new WxPayServiceImpl(wechatClient, Clock.fixed(NOW, ZoneOffset.UTC));
        config = new WxPayV3Config();
        config.setAppid("fixture-app");
        config.setMerchantId("fixture-merchant");
        config.setNotifyUrl("https://example.invalid/payment/notify");
        ReflectionTestUtils.setField(service, "orderInfoFeignClient", orderClient);
        ReflectionTestUtils.setField(service, "rechargeInfoFeignClient", rechargeClient);
        ReflectionTestUtils.setField(service, "paymentInfoService", paymentInfoService);
        ReflectionTestUtils.setField(service, "redissonClient", redissonClient);
        ReflectionTestUtils.setField(service, "wxPayV3Config", config);
    }

    private OrderInfo order() {
        OrderInfo order = new OrderInfo();
        order.setOrderNo(ORDER_NO);
        order.setUserId(USER_ID);
        order.setOrderStatus(SystemConstant.ORDER_STATUS_UNPAID);
        order.setPayWay(SystemConstant.ORDER_PAY_WAY_WEIXIN);
        order.setOrderAmount(new BigDecimal("19.90"));
        order.setOrderTitle("测试专辑");
        order.setCreateTime(Date.from(NOW.minusSeconds(300)));
        return order;
    }

    private PaymentInfo payment() {
        PaymentInfo payment = new PaymentInfo();
        payment.setOrderNo(ORDER_NO);
        payment.setUserId(USER_ID);
        payment.setPaymentType(SystemConstant.PAYMENT_TYPE_ORDER);
        payment.setPayWay(SystemConstant.ORDER_PAY_WAY_WEIXIN);
        payment.setPaymentStatus(SystemConstant.PAYMENT_STATUS_UNPAID);
        payment.setAmount(new BigDecimal("19.90"));
        return payment;
    }

    private void availableLock() throws InterruptedException {
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(2, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
    }

    @Test
    void ownedPendingOrder_usesServerAmountAndOrderDeadline() throws Exception {
        availableLock();
        when(orderClient.getOrderInfo(ORDER_NO)).thenReturn(Result.ok(order()));
        when(paymentInfoService.savePaymentInfo(SystemConstant.PAYMENT_TYPE_ORDER, ORDER_NO)).thenReturn(payment());
        PrepayResponse response = new PrepayResponse();
        response.setCodeUrl("weixin://wxpay/bizpayurl?pr=fixture-only");
        when(wechatClient.prepay(any())).thenReturn(response);

        assertEquals(response.getCodeUrl(), service.createNative("1301", ORDER_NO, USER_ID).get("codeUrl"));

        ArgumentCaptor<PrepayRequest> request = ArgumentCaptor.forClass(PrepayRequest.class);
        verify(wechatClient).prepay(request.capture());
        assertEquals(1990, request.getValue().getAmount().getTotal());
        assertEquals("CNY", request.getValue().getAmount().getCurrency());
        assertEquals("fixture-app", request.getValue().getAppid());
        assertEquals("fixture-merchant", request.getValue().getMchid());
        assertEquals(ORDER_NO, request.getValue().getOutTradeNo());
        assertEquals("https://example.invalid/payment/notify", request.getValue().getNotifyUrl());
        assertEquals(NOW.plusSeconds(1500), java.time.OffsetDateTime.parse(request.getValue().getTimeExpire()).toInstant());
        verify(lock).unlock();
    }

    private void payableOrder() throws InterruptedException {
        availableLock();
        when(orderClient.getOrderInfo(ORDER_NO)).thenReturn(Result.ok(order()));
        when(paymentInfoService.savePaymentInfo("1301", ORDER_NO)).thenReturn(payment());
    }

    @Test
    void invalidPaymentType_doesNotAccessDependencies() {
        assertThrows(IllegalArgumentException.class, () -> service.createNative("unknown", ORDER_NO, USER_ID));
        verifyNoInteractions(redissonClient, paymentInfoService, orderClient, rechargeClient, wechatClient);
    }

    @Test
    void missingUser_doesNotAccessDependencies() {
        assertThrows(IllegalArgumentException.class, () -> service.createNative("1301", ORDER_NO, null));
        verifyNoInteractions(redissonClient, paymentInfoService, orderClient, rechargeClient, wechatClient);
    }

    @Test
    void blankOrderNumber_doesNotAccessDependencies() {
        assertThrows(IllegalArgumentException.class, () -> service.createNative("1301", " ", USER_ID));
        verifyNoInteractions(redissonClient, paymentInfoService, orderClient, rechargeClient, wechatClient);
    }

    @Test
    void anotherUsersOrder_isRejectedBeforeSaving() throws Exception {
        availableLock();
        OrderInfo order = order();
        order.setUserId(99L);
        when(orderClient.getOrderInfo(ORDER_NO)).thenReturn(Result.ok(order));
        assertThrows(IllegalArgumentException.class, () -> service.createNative("1301", ORDER_NO, USER_ID));
        verifyNoInteractions(paymentInfoService, wechatClient);
        verify(lock).unlock();
    }

    @Test
    void paidOrder_isRejectedBeforeSaving() throws Exception {
        availableLock();
        OrderInfo order = order();
        order.setOrderStatus(SystemConstant.ORDER_STATUS_PAID);
        when(orderClient.getOrderInfo(ORDER_NO)).thenReturn(Result.ok(order));
        assertThrows(IllegalArgumentException.class, () -> service.createNative("1301", ORDER_NO, USER_ID));
        verifyNoInteractions(paymentInfoService, wechatClient);
    }

    @Test
    void cancelledOrder_isRejectedBeforeSaving() throws Exception {
        availableLock();
        OrderInfo order = order();
        order.setOrderStatus(SystemConstant.ORDER_STATUS_CANCEL);
        when(orderClient.getOrderInfo(ORDER_NO)).thenReturn(Result.ok(order));
        assertThrows(IllegalArgumentException.class, () -> service.createNative("1301", ORDER_NO, USER_ID));
        verifyNoInteractions(paymentInfoService, wechatClient);
    }

    @Test
    void nonWechatOrder_isRejectedBeforeSaving() throws Exception {
        availableLock();
        OrderInfo order = order();
        order.setPayWay("1103");
        when(orderClient.getOrderInfo(ORDER_NO)).thenReturn(Result.ok(order));
        assertThrows(IllegalArgumentException.class, () -> service.createNative("1301", ORDER_NO, USER_ID));
        verifyNoInteractions(paymentInfoService, wechatClient);
    }

    @Test
    void expiredOrder_isRejectedBeforeSaving() throws Exception {
        availableLock();
        OrderInfo order = order();
        order.setCreateTime(Date.from(NOW.minusSeconds(1801)));
        when(orderClient.getOrderInfo(ORDER_NO)).thenReturn(Result.ok(order));
        assertThrows(IllegalArgumentException.class, () -> service.createNative("1301", ORDER_NO, USER_ID));
        verifyNoInteractions(paymentInfoService, wechatClient);
    }

    private void rejectsAmount(BigDecimal amount) throws InterruptedException {
        availableLock();
        OrderInfo order = order();
        order.setOrderAmount(amount);
        when(orderClient.getOrderInfo(ORDER_NO)).thenReturn(Result.ok(order));
        assertThrows(IllegalArgumentException.class, () -> service.createNative("1301", ORDER_NO, USER_ID));
        verifyNoInteractions(paymentInfoService, wechatClient);
    }

    @Test
    void zeroAmount_isRejected() throws Exception { rejectsAmount(BigDecimal.ZERO); }

    @Test
    void negativeAmount_isRejected() throws Exception { rejectsAmount(new BigDecimal("-1.00")); }

    @Test
    void fractionalFen_isRejectedRatherThanRounded() throws Exception { rejectsAmount(new BigDecimal("1.001")); }

    @Test
    void integerFenOverflow_isRejected() throws Exception { rejectsAmount(new BigDecimal("21474836.48")); }

    @Test
    void failedUpstream_isNotTreatedAsAnOrder() throws Exception {
        availableLock();
        when(orderClient.getOrderInfo(ORDER_NO)).thenReturn(Result.fail());
        assertThrows(IllegalArgumentException.class, () -> service.createNative("1301", ORDER_NO, USER_ID));
        verifyNoInteractions(paymentInfoService, wechatClient);
    }

    @Test
    void inconsistentExistingPayment_isRejectedWithoutReusingIt() throws Exception {
        availableLock();
        when(orderClient.getOrderInfo(ORDER_NO)).thenReturn(Result.ok(order()));
        PaymentInfo payment = payment();
        payment.setUserId(99L);
        when(paymentInfoService.getOne(any())).thenReturn(payment);
        assertThrows(IllegalArgumentException.class, () -> service.createNative("1301", ORDER_NO, USER_ID));
        verify(paymentInfoService, never()).savePaymentInfo(anyString(), anyString());
        verifyNoInteractions(wechatClient);
    }

    @Test
    void inconsistentSavedPayment_isRejectedBeforeSdk() throws Exception {
        availableLock();
        when(orderClient.getOrderInfo(ORDER_NO)).thenReturn(Result.ok(order()));
        PaymentInfo payment = payment();
        payment.setAmount(new BigDecimal("0.01"));
        when(paymentInfoService.savePaymentInfo("1301", ORDER_NO)).thenReturn(payment);
        assertThrows(IllegalArgumentException.class, () -> service.createNative("1301", ORDER_NO, USER_ID));
        verifyNoInteractions(wechatClient);
    }

    @Test
    void cancelledDuringCreation_isRejectedBeforeSdk() throws Exception {
        availableLock();
        OrderInfo cancelled = order();
        cancelled.setOrderStatus(SystemConstant.ORDER_STATUS_CANCEL);
        when(orderClient.getOrderInfo(ORDER_NO)).thenReturn(Result.ok(order()), Result.ok(cancelled));
        when(paymentInfoService.savePaymentInfo("1301", ORDER_NO)).thenReturn(payment());
        assertThrows(IllegalArgumentException.class, () -> service.createNative("1301", ORDER_NO, USER_ID));
        verifyNoInteractions(wechatClient);
    }

    @Test
    void missingMerchantConfig_isRejectedBeforeSaving() throws Exception {
        availableLock();
        config.setMerchantId("");
        when(orderClient.getOrderInfo(ORDER_NO)).thenReturn(Result.ok(order()));
        assertThrows(IllegalArgumentException.class, () -> service.createNative("1301", ORDER_NO, USER_ID));
        verifyNoInteractions(paymentInfoService, wechatClient);
    }

    @Test
    void invalidSdkCodeUrl_isNotReturned() throws Exception {
        payableOrder();
        PrepayResponse response = new PrepayResponse();
        response.setCodeUrl("https://example.invalid/not-a-wechat-code");
        when(wechatClient.prepay(any())).thenReturn(response);
        assertThrows(IllegalArgumentException.class, () -> service.createNative("1301", ORDER_NO, USER_ID));
        verify(lock).unlock();
    }

    @Test
    void sdkFailure_isPropagatedAndLockReleased() throws Exception {
        payableOrder();
        when(wechatClient.prepay(any())).thenThrow(new IllegalStateException("fixture gateway unavailable"));
        assertThrows(IllegalStateException.class, () -> service.createNative("1301", ORDER_NO, USER_ID));
        verify(lock).unlock();
    }

    @Test
    void concurrentCreation_doesNotReachSdk() throws Exception {
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(2, TimeUnit.SECONDS)).thenReturn(false);
        assertThrows(IllegalStateException.class, () -> service.createNative("1301", ORDER_NO, USER_ID));
        verifyNoInteractions(paymentInfoService, orderClient, rechargeClient, wechatClient);
        verify(lock, never()).unlock();
    }

    @Test
    void interruptedLock_restoresThreadInterrupt() throws Exception {
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(2, TimeUnit.SECONDS)).thenThrow(new InterruptedException("fixture"));
        try {
            assertThrows(IllegalStateException.class, () -> service.createNative("1301", ORDER_NO, USER_ID));
            assertTrue(Thread.currentThread().isInterrupted());
            verifyNoInteractions(paymentInfoService, orderClient, rechargeClient, wechatClient);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void ownedRecharge_usesRealAmountWithoutOpenidOrInventedDeadline() throws Exception {
        availableLock();
        RechargeInfo recharge = new RechargeInfo();
        recharge.setOrderNo(ORDER_NO);
        recharge.setUserId(USER_ID);
        recharge.setRechargeStatus(SystemConstant.ORDER_STATUS_UNPAID);
        recharge.setPayWay(SystemConstant.ORDER_PAY_WAY_WEIXIN);
        recharge.setRechargeAmount(new BigDecimal("19.90"));
        when(rechargeClient.getRechargeInfo(ORDER_NO)).thenReturn(Result.ok(recharge));
        PaymentInfo payment = payment();
        payment.setPaymentType(SystemConstant.PAYMENT_TYPE_RECHARGE);
        when(paymentInfoService.savePaymentInfo("1302", ORDER_NO)).thenReturn(payment);
        PrepayResponse response = new PrepayResponse();
        response.setCodeUrl("weixin://wxpay/bizpayurl?pr=recharge-fixture");
        when(wechatClient.prepay(any())).thenReturn(response);
        assertEquals(response.getCodeUrl(), service.createNative("1302", ORDER_NO, USER_ID).get("codeUrl"));
        ArgumentCaptor<PrepayRequest> request = ArgumentCaptor.forClass(PrepayRequest.class);
        verify(wechatClient).prepay(request.capture());
        assertEquals(1990, request.getValue().getAmount().getTotal());
        assertEquals(null, request.getValue().getTimeExpire());
        verifyNoInteractions(orderClient);
    }

    @Test
    void paymentQuery_checksCurrentUserBeforeCallingWechat() {
        PaymentInfo payment = payment();
        payment.setUserId(99L);
        when(paymentInfoService.getOne(any())).thenReturn(payment);
        assertThrows(IllegalArgumentException.class, () -> service.queryPayStatus(ORDER_NO, USER_ID));
        verifyNoInteractions(wechatClient);
    }

    @Test
    void paymentQuery_rejectsMismatchedWechatOrder() {
        when(paymentInfoService.getOne(any())).thenReturn(payment());
        Transaction transaction = new Transaction();
        transaction.setOutTradeNo("another-order");
        when(wechatClient.queryPayStatus(ORDER_NO)).thenReturn(transaction);
        assertThrows(IllegalArgumentException.class, () -> service.queryPayStatus(ORDER_NO, USER_ID));
    }

    @Test
    void oldJsapiEntry_usesSameLockAndCannotBypassOwnership() throws Exception {
        availableLock();
        OrderInfo order = order();
        order.setUserId(99L);
        when(orderClient.getOrderInfo(ORDER_NO)).thenReturn(Result.ok(order));
        assertThrows(IllegalArgumentException.class, () -> service.createJsapi("1301", ORDER_NO, USER_ID));
        verify(redissonClient).getLock("payment:wechat:create:" + ORDER_NO);
        verifyNoInteractions(paymentInfoService, wechatClient);
        verify(lock).unlock();
    }
}
