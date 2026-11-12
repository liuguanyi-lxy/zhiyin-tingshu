package com.atguigu.tingshu.account.api;

import com.atguigu.tingshu.account.security.AccountInternalAuth;
import com.atguigu.tingshu.account.service.UserAccountService;
import com.atguigu.tingshu.common.constant.AccountInternalConstant;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.handler.GlobalExceptionHandler;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import com.atguigu.tingshu.vo.account.AccountLockVo;
import com.atguigu.tingshu.vo.account.AccountRecordVo;
import com.atguigu.tingshu.common.login.LoginVerification;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class AccountDeductSecurityTest {
    private static final String TEST_CREDENTIAL = "test-only-internal-credential-not-for-deployment";
    private static final String BODY = "{\"userId\":8,\"amount\":19.90,\"orderNo\":\"ORDER123\"}";

    private MockMvc mvc(UserAccountService service, String configuredCredential) {
        UserAccountApiController controller = new UserAccountApiController();
        ReflectionTestUtils.setField(controller, "userAccountService", service);
        ReflectionTestUtils.setField(controller, "accountInternalAuth", new AccountInternalAuth(configuredCredential));
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"forged", "   "})
    void userLoginOrForgedHeaderCannotAuthorizeDeduction(String supplied) throws Exception {
        UserAccountService service = mock(UserAccountService.class);
        var request = post(AccountInternalConstant.DEDUCT_PATH)
                .contentType("application/json").content(BODY).header("token", "ordinary-user-token");
        if (supplied != null) { request.header(AccountInternalConstant.HEADER, supplied); }
        mvc(service, TEST_CREDENTIAL).perform(request)
                .andExpect(jsonPath("$.code").value(ResultCodeEnum.PERMISSION.getCode()));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"short", "                                "})
    void missingOrWeakConfigurationFailsClosed(String configured) {
        GuiguException error = assertThrows(GuiguException.class,
                () -> new AccountInternalAuth(configured).requireTrustedCaller(TEST_CREDENTIAL));
        assertEquals(ResultCodeEnum.PERMISSION.getCode(), error.getCode());
    }

    @Test
    void trustedInternalRequestReachesService() throws Exception {
        UserAccountService service = mock(UserAccountService.class);
        when(service.checkAndDeduct(any())).thenReturn(Result.ok());
        mvc(service, TEST_CREDENTIAL).perform(post(AccountInternalConstant.DEDUCT_PATH)
                .contentType("application/json").content(BODY)
                .header(AccountInternalConstant.HEADER, TEST_CREDENTIAL))
                .andExpect(jsonPath("$.code").value(ResultCodeEnum.SUCCESS.getCode()));
        verify(service).checkAndDeduct(any(AccountLockVo.class));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"ordinary-user-token", "forged-internal-token"})
    void ordinaryUserCannotInitializeAnotherUsersAccount(String supplied) throws Exception {
        UserAccountService service = mock(UserAccountService.class);
        var request = post(AccountInternalConstant.INIT_ACCOUNT_PATH_PREFIX + "8")
                .header("token", "ordinary-user-token");
        if (supplied != null) { request.header(AccountInternalConstant.HEADER, supplied); }
        mvc(service, TEST_CREDENTIAL).perform(request)
                .andExpect(jsonPath("$.code").value(ResultCodeEnum.PERMISSION.getCode()));
        verifyNoInteractions(service);
    }

    @Test
    void trustedInternalRequestCanInitializeAccount() throws Exception {
        UserAccountService service = mock(UserAccountService.class);
        mvc(service, TEST_CREDENTIAL).perform(post(AccountInternalConstant.INIT_ACCOUNT_PATH_PREFIX + "8")
                .header(AccountInternalConstant.HEADER, TEST_CREDENTIAL))
                .andExpect(jsonPath("$.code").value(ResultCodeEnum.SUCCESS.getCode()));
        verify(service).addUserAccount(8L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"findUserConsumePage", "findUserRechargePage"})
    void recordPagesRequireLoginAndIgnoreCallerSuppliedUserId(String method) throws Exception {
        var endpoint = UserAccountApiController.class.getMethod(method, long.class, long.class);
        assertNotNull(endpoint.getAnnotation(LoginVerification.class));
        assertTrue(endpoint.getAnnotation(LoginVerification.class).required());
        UserAccountService service = mock(UserAccountService.class);
        Page<AccountRecordVo> page = new Page<>(1, 10, 0);
        if (method.equals("findUserConsumePage")) when(service.findUserConsumePage(8L, 1, 10)).thenReturn(page);
        else when(service.findUserRechargePage(8L, 1, 10)).thenReturn(page);
        AuthContextHolder.setUserId(8L);
        try {
            mvc(service, TEST_CREDENTIAL).perform(get("/api/account/userAccount/" + method + "/1/10").param("userId", "9999"))
                    .andExpect(jsonPath("$.code").value(ResultCodeEnum.SUCCESS.getCode()))
                    .andExpect(jsonPath("$.data.current").value(1))
                    .andExpect(jsonPath("$.data.total").value(0));
            if (method.equals("findUserConsumePage")) verify(service).findUserConsumePage(8L, 1, 10);
            else verify(service).findUserRechargePage(8L, 1, 10);
            verifyNoMoreInteractions(service);
        } finally {
            AuthContextHolder.removeUserId();
        }
    }

    @Test
    void accountInitializationRejectsNonPositiveUserId() throws Exception {
        UserAccountService service = mock(UserAccountService.class);
        mvc(service, TEST_CREDENTIAL).perform(post(AccountInternalConstant.INIT_ACCOUNT_PATH_PREFIX + "0")
                .header(AccountInternalConstant.HEADER, TEST_CREDENTIAL))
                .andExpect(jsonPath("$.code").value(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode()));
        verifyNoInteractions(service);
    }
}
