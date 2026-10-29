package com.atguigu.tingshu.account.api;

import com.atguigu.tingshu.account.client.config.AccountDeductFeignConfiguration;
import com.atguigu.tingshu.common.constant.AccountInternalConstant;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AccountDeductFeignTest {
    private static final String TEST_CREDENTIAL = "test-only-internal-credential-not-for-deployment";

    @Test
    void deductionCarriesConfiguredCredentialAndReplacesForgedHeader() {
        RequestTemplate template = new RequestTemplate();
        template.uri(AccountInternalConstant.DEDUCT_PATH);
        template.header(AccountInternalConstant.HEADER, "forged");
        new AccountDeductFeignConfiguration().accountDeductInterceptor(TEST_CREDENTIAL).apply(template);
        assertEquals(java.util.List.of(TEST_CREDENTIAL),
                java.util.List.copyOf(template.headers().get(AccountInternalConstant.HEADER)));
    }

    @Test
    void missingCredentialStopsDeductionBeforeNetworkCall() {
        RequestTemplate template = new RequestTemplate();
        template.uri(AccountInternalConstant.DEDUCT_PATH);
        assertThrows(IllegalStateException.class,
                () -> new AccountDeductFeignConfiguration().accountDeductInterceptor("").apply(template));
    }

    @Test
    void weakCredentialStopsDeductionBeforeNetworkCall() {
        RequestTemplate template = new RequestTemplate();
        template.uri(AccountInternalConstant.DEDUCT_PATH);
        assertThrows(IllegalStateException.class,
                () -> new AccountDeductFeignConfiguration().accountDeductInterceptor("short").apply(template));
    }

    @Test
    void credentialIsNotSentToAccountInitialization() {
        RequestTemplate template = new RequestTemplate();
        template.uri("/api/account/userAccount/addUserAccount/8");
        new AccountDeductFeignConfiguration().accountDeductInterceptor(TEST_CREDENTIAL).apply(template);
        assertFalse(template.headers().containsKey(AccountInternalConstant.HEADER));
    }

    @Test
    void missingCredentialDoesNotBreakUnrelatedClientMethods() {
        RequestTemplate template = new RequestTemplate();
        template.uri("/api/account/userAccount/addUserAccount/8");
        assertDoesNotThrow(() -> new AccountDeductFeignConfiguration().accountDeductInterceptor("").apply(template));
    }
}
