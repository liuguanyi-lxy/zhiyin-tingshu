package com.atguigu.tingshu.common.filter;

import com.atguigu.tingshu.common.util.TraceContextUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 链路追踪过滤器单元测试（spring-test Mock Servlet 对象，无容器）。
 * 覆盖：上游透传 traceId 复用并回写响应头、无头自动生成、请求结束后 MDC 清理。
 */
class TraceFilterTest {

    private final TraceFilter filter = new TraceFilter();

    @AfterEach
    void cleanUp() {
        TraceContextUtil.end();
    }

    @Test
    void reusesUpstreamTraceIdAndWritesResponseHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceContextUtil.HEADER_TRACE_ID, "from-gateway");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals("from-gateway", response.getHeader(TraceContextUtil.HEADER_TRACE_ID));
    }

    @Test
    void generatesTraceIdWhenHeaderMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        String traceId = response.getHeader(TraceContextUtil.HEADER_TRACE_ID);
        assertEquals(32, traceId.length());
    }

    @Test
    void traceIdVisibleInsideChainAndClearedAfterwards() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceContextUtil.HEADER_TRACE_ID, "inside-chain");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain[] holder = new MockFilterChain[1];
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(ServletRequest req, ServletResponse res)
                    throws IOException, ServletException {
                // 过滤器链内 traceId 必须可见（供控制器/AOP 日志输出）
                holder[0] = this;
                assertEquals("inside-chain", TraceContextUtil.getTraceId());
            }
        };

        filter.doFilter(request, response, chain);

        // 请求结束后必须清理，防止线程池复用串号
        assertNull(TraceContextUtil.getTraceId());
    }
}
