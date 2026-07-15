package com.atguigu.tingshu.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * <p>
 * 全局Filter，统一处理会员登录与外部不允许访问的服务
 * Spring Cloud Gateway的工作机制：
 *
 * 基于Spring WebFlux和响应式编程构建
 * 请求通过一系列过滤器链进行处理
 * 全局过滤器会对所有路由生效
 * 过滤器链执行流程：
 *
 * 请求进入网关后，会经过所有全局过滤器
 * 每个过滤器可以决定是否继续处理链(chain.filter(exchange))
 * 也可以直接返回响应，不继续执行后续过滤器
 * </p>
 *
 */
@Slf4j
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    /*这是GlobalFilter接口的核心方法，定义了过滤器的实际逻辑
    当前实现非常简单，只是将请求传递给过滤器链中的下一个过滤器
    返回类型是Mono<Void>，这是Spring WebFlux中的响应式编程类型，表示一个异步操作*/
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange);
    }
    /*这是Ordered接口的方法，用于确定过滤器的执行顺序
    返回值越小，过滤器优先级越高
    这里返回0，表示较高的优先级（默认值）*/
    @Override
    public int getOrder() {
        return 0;
    }
}
