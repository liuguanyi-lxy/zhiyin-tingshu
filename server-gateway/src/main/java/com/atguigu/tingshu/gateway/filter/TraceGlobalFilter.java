package com.atguigu.tingshu.gateway.filter;

import com.atguigu.tingshu.common.util.TraceContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关链路追踪过滤器：生成/透传 traceId，让整条调用链日志可串联。
 * <p>
 * order 取 {@link Integer#MIN_VALUE}，保证在 {@link AuthGlobalFilter}(order=0) 之前执行，
 * 这样鉴权拒绝(208)的日志也带 traceId，且 208 响应头回带 X-Request-Id。
 * </p>
 * <p>
 * 注意：WebFlux 是响应式环境，MDC 默认只对当前执行线程有效（线程切换时可能丢失）。
 * 本项目网关自身日志以 filter 内同步逻辑为主（请求进入、鉴权拒绝），能正确带上 traceId；
 * 下游服务的日志由各服务 TraceFilter 保证（从 header 读到 traceId）。够用且不过度设计。
 * </p>
 */
@Slf4j
@Component
public class TraceGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        //1. 取前端透传的 X-Request-Id，没有则生成新 traceId 并写入 MDC
        String headerTraceId = exchange.getRequest().getHeaders().getFirst(TraceContextUtil.HEADER_TRACE_ID);
        // 从 lambda 外确定最终 traceId（lambda 引用的变量须为 effectively final）
        final String traceId;
        if (headerTraceId == null || headerTraceId.isEmpty()) {
            traceId = TraceContextUtil.begin();
        } else {
            traceId = headerTraceId;
            MDC.put(TraceContextUtil.TRACE_ID, traceId);
        }
        //2. 透传 header 给下游服务（各服务 TraceFilter 会从该 header 取 traceId）
        ServerWebExchange newExchange = exchange.mutate()
                .request(r -> r.headers(h -> h.set(TraceContextUtil.HEADER_TRACE_ID, traceId)))
                .build();
        //3. 回写响应头，前端可拿到本次请求的 traceId 反馈问题
        newExchange.getResponse().getHeaders().set(TraceContextUtil.HEADER_TRACE_ID, traceId);
        log.info("[网关] 请求进入 path={}, traceId={}", newExchange.getRequest().getURI().getPath(), traceId);
        //4. 请求完成后清理 MDC，防止线程复用串号
        return chain.filter(newExchange)
                .doFinally(signal -> MDC.remove(TraceContextUtil.TRACE_ID));
    }

    @Override
    public int getOrder() {
        return Integer.MIN_VALUE;
    }
}
