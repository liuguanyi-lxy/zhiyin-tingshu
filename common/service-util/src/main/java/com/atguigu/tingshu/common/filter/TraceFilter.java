package com.atguigu.tingshu.common.filter;

import com.atguigu.tingshu.common.util.TraceContextUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 链路追踪过滤器：把上游透传的 traceId 写入 MDC，请求结束清理。
 * <p>
 * 作为 Spring Boot 自动注册的 {@code @Component} Filter，对所有请求生效（含 /error、异常路径），
 * 这样控制器、AOP（LoginAspect）、全局异常处理器里的日志都能带 traceId。
 * traceId 来源：优先取上游 header {@code X-Request-Id}（网关/Feign 透传），没有则新生成。
 * </p>
 */
@Slf4j
@Component
public class TraceFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String traceId = request.getHeader(TraceContextUtil.HEADER_TRACE_ID);
            TraceContextUtil.begin(traceId);
            // 回写响应头，前端/调用方可通过 X-Request-Id 拿到本次请求的 traceId 便于排查问题
            response.setHeader(TraceContextUtil.HEADER_TRACE_ID, TraceContextUtil.getTraceId());
            chain.doFilter(request, response);
        } finally {
            // 必须清理，防止 Tomcat 线程池复用导致 traceId 串号
            TraceContextUtil.end();
        }
    }
}
