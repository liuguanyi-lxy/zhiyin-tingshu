package com.atguigu.tingshu.common.aspect;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.atguigu.tingshu.common.annotation.Log;
import com.atguigu.tingshu.common.enums.BusinessType;
import com.atguigu.tingshu.common.enums.OperatorType;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 操作日志切面单元测试。
 * 覆盖：正常路径 JSON 组装（标题/业务类型/方法/操作人/状态）、异常路径（状态0+错误消息）、
 * 输出走 [OPER_LOG] 结构化前缀（即 ELK 检索锚点）。
 */
@ExtendWith(MockitoExtension.class)
class LogAspectTest {

    private final LogAspect aspect = new LogAspect();

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @Mock
    private JoinPoint joinPoint;
    @Mock
    private Log logAnnotation;
    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        appender.start();
        ((Logger) LoggerFactory.getLogger(LogAspect.class)).addAppender(appender);

        // 匿名目标类：让 joinPoint.getTarget().getClass().getName() 有真实值
        Object target = new Object() {};
        lenient().when(joinPoint.getTarget()).thenReturn(target);
        lenient().when(joinPoint.getSignature()).thenReturn(mock(org.aspectj.lang.Signature.class));
        lenient().when(joinPoint.getSignature().getName()).thenReturn("submitOrder");

        lenient().when(request.getRequestURI()).thenReturn("/api/order/orderInfo/submitOrder");
        lenient().when(request.getMethod()).thenReturn("POST");
        lenient().when(request.getHeader("x-forwarded-for")).thenReturn("127.0.0.1");

        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        lenient().when(logAnnotation.title()).thenReturn("订单提交");
        lenient().when(logAnnotation.businessType()).thenReturn(BusinessType.INSERT);
        lenient().when(logAnnotation.operatorType()).thenReturn(OperatorType.MOBILE);
        lenient().when(logAnnotation.isSaveRequestData()).thenReturn(true);
        lenient().when(logAnnotation.isSaveResponseData()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(LogAspect.class)).detachAppender(appender);
        RequestContextHolder.resetRequestAttributes();
        AuthContextHolder.removeUserId();
    }

    private List<String> operLogMessages() {
        return appender.list.stream()
                .filter(event -> event.getFormattedMessage().startsWith(LogAspect.OPER_LOG_MARKER))
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.toList());
    }

    @Test
    void successPath_logsStructuredOperLogWithOperator() {
        AuthContextHolder.setUserId(9L);

        aspect.doAfterReturning(joinPoint, logAnnotation, "result");

        List<String> messages = operLogMessages();
        assertEquals(1, messages.size());
        String json = messages.get(0);
        assertTrue(json.contains("\"title\":\"订单提交\""));
        assertTrue(json.contains("\"businessType\":\"INSERT\""));
        assertTrue(json.contains("\"operatorType\":\"MOBILE\""));
        assertTrue(json.contains("submitOrder"));
        // 操作人取自 ThreadLocal 登录上下文
        assertTrue(json.contains("\"operName\":\"9\""));
        assertTrue(json.contains("\"status\":1"));
        assertTrue(json.contains("\"operIp\":\"127.0.0.1\""));
    }

    @Test
    void anonymousRequest_operNameStayNull() {
        aspect.doAfterReturning(joinPoint, logAnnotation, "result");

        List<String> messages = operLogMessages();
        assertEquals(1, messages.size());
        // 支付回调等匿名接口没有登录上下文：fastjson 默认不序列化 null 字段，
        // 断言 JSON 中不含 operName 键（不会误绑到其他用户）
        String json = messages.get(0);
        assertTrue(!json.contains("\"operName\""));
    }

    @Test
    void exceptionPath_logsStatusZeroWithErrorMsg() {
        AuthContextHolder.setUserId(9L);

        aspect.doAfterThrowing(joinPoint, logAnnotation, new IllegalStateException("库存不足"));

        List<String> messages = operLogMessages();
        assertEquals(1, messages.size());
        String json = messages.get(0);
        assertTrue(json.contains("\"status\":0"));
        assertTrue(json.contains("库存不足"));
    }

    @Test
    void aspectFailure_neverThrowsAndLogsError() {
        // 请求上下文缺失（如非 Web 线程调用）：切面吞掉自身异常，不影响业务
        RequestContextHolder.resetRequestAttributes();

        aspect.doAfterReturning(joinPoint, logAnnotation, "result");

        List<ILoggingEvent> errors = appender.list.stream()
                .filter(event -> event.getLevel() == Level.ERROR)
                .collect(Collectors.toList());
        assertEquals(1, errors.size());
        assertTrue(operLogMessages().isEmpty());
    }
}
