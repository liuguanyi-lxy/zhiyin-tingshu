package com.atguigu.tingshu.account.api;

import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.login.LoginVerification;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.account.service.RechargeInfoService;
import com.atguigu.tingshu.model.account.RechargeInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 充值记录详情接口归属校验单元测试（纯 Mockito，不启动容器/中间件）。
 * 覆盖三种情形：本人查询通过、他人查询被拒、无用户上下文（模拟 Feign 内部调用）放行。
 */
@ExtendWith(MockitoExtension.class)
class RechargeInfoApiControllerTest {

    private static final String RECHARGE_NO = "RECHARGE123";

    @Mock
    private RechargeInfoService rechargeInfoService;

    private final RechargeInfoApiController controller = new RechargeInfoApiController();

    private RechargeInfo ownedRecharge() {
        RechargeInfo rechargeInfo = new RechargeInfo();
        rechargeInfo.setOrderNo(RECHARGE_NO);
        rechargeInfo.setUserId(8L);
        return rechargeInfo;
    }

    @AfterEach
    void clearContext() {
        // 清理 ThreadLocal，避免用户上下文串到其他测试。
        AuthContextHolder.removeUserId();
    }

    @Test
    void ownerQuery_passes() {
        ReflectionTestUtils.setField(controller, "rechargeInfoService", rechargeInfoService);
        AuthContextHolder.setUserId(8L);
        RechargeInfo rechargeInfo = ownedRecharge();
        when(rechargeInfoService.getRechargeInfo(RECHARGE_NO, 8L)).thenReturn(rechargeInfo);

        assertEquals(rechargeInfo, controller.getRechargeInfo(RECHARGE_NO).getData());
        verify(rechargeInfoService).getRechargeInfo(RECHARGE_NO, 8L);
    }

    @Test
    void anotherUserQuery_rejected() {
        ReflectionTestUtils.setField(controller, "rechargeInfoService", rechargeInfoService);
        AuthContextHolder.setUserId(9L);
        // 防护方法对非本人抛 209（没有权限），Controller 原样透出给全局异常处理。
        when(rechargeInfoService.getRechargeInfo(RECHARGE_NO, 9L))
                .thenThrow(new GuiguException(ResultCodeEnum.PERMISSION));

        GuiguException exception = assertThrows(GuiguException.class,
                () -> controller.getRechargeInfo(RECHARGE_NO));
        assertEquals(ResultCodeEnum.PERMISSION.getCode(), exception.getCode());
    }

    @Test
    void internalCallWithoutUserContext_passes() {
        ReflectionTestUtils.setField(controller, "rechargeInfoService", rechargeInfoService);
        // 不设置用户上下文，模拟 Feign/异步等内部调用：以 null userId 调用防护方法，直接放行。
        RechargeInfo rechargeInfo = ownedRecharge();
        when(rechargeInfoService.getRechargeInfo(eq(RECHARGE_NO), isNull())).thenReturn(rechargeInfo);

        assertSame(rechargeInfo, controller.getRechargeInfo(RECHARGE_NO).getData());
        verify(rechargeInfoService).getRechargeInfo(eq(RECHARGE_NO), isNull());
    }

    @Test
    void getRechargeInfo_requiresLogin() throws Exception {
        // 该路径在网关白名单中（匿名请求可到达服务端），必须在服务层强制登录，
        // 否则 LoginAspect 不写入用户上下文，归属校验形同虚设。
        assertTrue(RechargeInfoApiController.class.getMethod("getRechargeInfo", String.class)
                .getAnnotation(LoginVerification.class).required());
    }
}
