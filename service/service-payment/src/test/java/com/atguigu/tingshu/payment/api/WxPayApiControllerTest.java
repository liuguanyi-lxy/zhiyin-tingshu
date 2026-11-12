package com.atguigu.tingshu.payment.api;

import com.atguigu.tingshu.common.login.LoginVerification;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.payment.config.WxPayV3Config;
import com.atguigu.tingshu.payment.service.PaymentInfoService;
import com.atguigu.tingshu.payment.service.WxPayService;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.payments.model.TransactionAmount;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 不启动证书配置、不访问微信，只验证外部入口和成功状态的失败关闭守卫。 */
@ExtendWith(MockitoExtension.class)
class WxPayApiControllerTest {
    private static final String ORDER_NO = "order123";
    @Mock private WxPayService wxPayService;
    @Mock private PaymentInfoService paymentInfoService;
    private WxPayApiController controller;

    @BeforeEach
    void setUp() {
        controller = new WxPayApiController();
        WxPayV3Config config = new WxPayV3Config();
        config.setAppid("fixture-app");
        config.setMerchantId("fixture-merchant");
        ReflectionTestUtils.setField(controller, "wxPayService", wxPayService);
        ReflectionTestUtils.setField(controller, "paymentInfoService", paymentInfoService);
        ReflectionTestUtils.setField(controller, "wxPayV3Config", config);
        AuthContextHolder.setUserId(7L);
    }

    @AfterEach
    void clearContext() {
        AuthContextHolder.removeUserId();
    }

    private Transaction success() {
        Transaction transaction = new Transaction();
        transaction.setOutTradeNo(ORDER_NO);
        transaction.setMchid("fixture-merchant");
        transaction.setAppid("fixture-app");
        transaction.setTradeState(Transaction.TradeStateEnum.SUCCESS);
        TransactionAmount amount = new TransactionAmount();
        amount.setTotal(1990);
        amount.setCurrency("CNY");
        transaction.setAmount(amount);
        return transaction;
    }

    @Test
    void nativeCreation_usesAuthenticatedUserAndRequiresLogin() throws Exception {
        Map<String, String> code = Map.of("codeUrl", "weixin://wxpay/bizpayurl?pr=fixture-only");
        when(wxPayService.createNative("1301", ORDER_NO, 7L)).thenReturn(code);
        assertEquals(code, controller.createNative("1301", ORDER_NO).getData());
        verify(wxPayService).createNative("1301", ORDER_NO, 7L);
        assertTrue(WxPayApiController.class.getMethod("createNative", String.class, String.class)
                .getAnnotation(LoginVerification.class).required());
        assertTrue(WxPayApiController.class.getMethod("queryPayStatus", String.class)
                .getAnnotation(LoginVerification.class).required());
    }

    @Test
    void ownedSuccessfulQuery_updatesOnlyAfterMerchantAndCurrencyChecks() {
        Transaction transaction = success();
        when(wxPayService.queryPayStatus(ORDER_NO, 7L)).thenReturn(transaction);
        assertEquals(Boolean.TRUE, controller.queryPayStatus(ORDER_NO).getData());
        verify(paymentInfoService).updatePaymentStatus(transaction);
    }

    @Test
    void pendingTransaction_isNotReportedSuccessful() {
        Transaction transaction = success();
        transaction.setTradeState(Transaction.TradeStateEnum.NOTPAY);
        when(wxPayService.queryPayStatus(ORDER_NO, 7L)).thenReturn(transaction);
        assertEquals(Boolean.FALSE, controller.queryPayStatus(ORDER_NO).getData());
        verifyNoInteractions(paymentInfoService);
    }

    private void assertRejected(Transaction transaction) {
        when(wxPayService.queryPayStatus(ORDER_NO, 7L)).thenReturn(transaction);
        assertThrows(IllegalArgumentException.class, () -> controller.queryPayStatus(ORDER_NO));
        verifyNoInteractions(paymentInfoService);
    }

    @Test
    void anotherMerchant_isRejectedBeforeUpdating() {
        Transaction transaction = success();
        transaction.setMchid("another-merchant");
        assertRejected(transaction);
    }

    @Test
    void anotherApplication_isRejectedBeforeUpdating() {
        Transaction transaction = success();
        transaction.setAppid("another-app");
        assertRejected(transaction);
    }

    @Test
    void anotherCurrency_isRejectedBeforeUpdating() {
        Transaction transaction = success();
        transaction.getAmount().setCurrency("USD");
        assertRejected(transaction);
    }

    @Test
    void anotherOrder_isRejectedBeforeUpdating() {
        Transaction transaction = success();
        transaction.setOutTradeNo("another-order");
        assertRejected(transaction);
    }

    @Test
    void missingAmount_isRejectedBeforeUpdating() {
        Transaction transaction = success();
        transaction.setAmount(null);
        assertRejected(transaction);
    }

    @Test
    void zeroAmount_isRejectedBeforeUpdating() {
        Transaction transaction = success();
        transaction.getAmount().setTotal(0);
        assertRejected(transaction);
    }

    @Test
    void failedOwnershipQuery_doesNotUpdatePayment() {
        when(wxPayService.queryPayStatus(ORDER_NO, 7L)).thenThrow(new IllegalArgumentException("无权查询"));
        assertThrows(IllegalArgumentException.class, () -> controller.queryPayStatus(ORDER_NO));
        verifyNoInteractions(paymentInfoService);
    }

    @Test
    void callbackGuard_rejectsNonSuccessState() {
        Transaction transaction = success();
        transaction.setTradeState(Transaction.TradeStateEnum.CLOSED);
        assertThrows(IllegalArgumentException.class, () -> ReflectionTestUtils.invokeMethod(
                controller, "validateSuccessfulTransaction", transaction, null));
        verifyNoInteractions(paymentInfoService);
    }
}
