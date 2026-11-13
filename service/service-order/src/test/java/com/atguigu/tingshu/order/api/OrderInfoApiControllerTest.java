package com.atguigu.tingshu.order.api;

import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.login.LoginVerification;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.model.order.OrderInfo;
import com.atguigu.tingshu.order.service.OrderInfoService;
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
 * 订单详情接口归属校验单元测试（纯 Mockito，不启动容器/中间件）。
 * 覆盖三种情形：本人查询通过、他人查询被拒、无用户上下文（模拟 Feign 内部调用）放行。
 */
@ExtendWith(MockitoExtension.class)
class OrderInfoApiControllerTest {

    private static final String ORDER_NO = "ORDER123";

    @Mock
    private OrderInfoService orderInfoService;

    private final OrderInfoApiController controller = new OrderInfoApiController();

    private OrderInfo ownedOrder() {
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setOrderNo(ORDER_NO);
        orderInfo.setUserId(8L);
        return orderInfo;
    }

    @AfterEach
    void clearContext() {
        // 清理 ThreadLocal，避免用户上下文串到其他测试。
        AuthContextHolder.removeUserId();
    }

    @Test
    void ownerQuery_passes() {
        ReflectionTestUtils.setField(controller, "orderInfoService", orderInfoService);
        AuthContextHolder.setUserId(8L);
        OrderInfo orderInfo = ownedOrder();
        when(orderInfoService.getOrderInfoByOrderNo(ORDER_NO, 8L)).thenReturn(orderInfo);

        assertEquals(orderInfo, controller.getOrderInfo(ORDER_NO).getData());
        verify(orderInfoService).getOrderInfoByOrderNo(ORDER_NO, 8L);
    }

    @Test
    void anotherUserQuery_rejected() {
        ReflectionTestUtils.setField(controller, "orderInfoService", orderInfoService);
        AuthContextHolder.setUserId(9L);
        // 防护方法对非本人抛 209（没有权限），Controller 原样透出给全局异常处理。
        when(orderInfoService.getOrderInfoByOrderNo(ORDER_NO, 9L))
                .thenThrow(new GuiguException(ResultCodeEnum.PERMISSION));

        GuiguException exception = assertThrows(GuiguException.class,
                () -> controller.getOrderInfo(ORDER_NO));
        assertEquals(ResultCodeEnum.PERMISSION.getCode(), exception.getCode());
    }

    @Test
    void internalCallWithoutUserContext_passes() {
        ReflectionTestUtils.setField(controller, "orderInfoService", orderInfoService);
        // 不设置用户上下文，模拟 Feign/异步等内部调用：以 null userId 调用防护方法，直接放行。
        OrderInfo orderInfo = ownedOrder();
        when(orderInfoService.getOrderInfoByOrderNo(eq(ORDER_NO), isNull())).thenReturn(orderInfo);

        assertSame(orderInfo, controller.getOrderInfo(ORDER_NO).getData());
        verify(orderInfoService).getOrderInfoByOrderNo(eq(ORDER_NO), isNull());
    }

    @Test
    void getOrderInfo_requiresLogin() throws Exception {
        // 与 payment 模块 queryPayStatus 同一模式：对外入口必须登录，归属校验才有意义。
        assertTrue(OrderInfoApiController.class.getMethod("getOrderInfo", String.class)
                .getAnnotation(LoginVerification.class).required());
    }
}
