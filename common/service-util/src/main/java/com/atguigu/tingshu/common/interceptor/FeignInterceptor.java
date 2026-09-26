package com.atguigu.tingshu.common.interceptor;

import com.atguigu.tingshu.common.util.TraceContextUtil;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Feign 请求头透传拦截器。
 *
 * <p>一次用户请求可能经过多个微服务。token 用于让下游服务继续完成登录校验，traceId 用于把
 * 多个服务的日志串成一条调用链。没有 Servlet 请求时（例如 MQ 消费者调用 Feign），只透传或创建 traceId。</p>
 */
@Component
public class FeignInterceptor implements RequestInterceptor {

    /**
     * 实现RequestInterceptor接口的apply方法，用于拦截并修改Feign请求
     * @param requestTemplate Feign请求模板，可以添加、修改请求头等信息
     */
    public void apply(RequestTemplate requestTemplate){
        // HTTP 线程中可以从 RequestContextHolder 取得上游请求；异步线程通常没有这个上下文。
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        // 异步编排与 MQ 消费线程的 requestAttributes 为 null，此时不能读取 token。
        if(null != requestAttributes) {
            ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes)requestAttributes;
            HttpServletRequest request = servletRequestAttributes.getRequest();
            // token 必须沿着 Feign 调用链透传，否则下游 LoginAspect 会认为用户未登录。
            String token = request.getHeader("token");
            // 将 token 添加到下游请求头。
            requestTemplate.header("token", token);
        }
        //透传traceId：从MDC取（HTTP线程已被TraceFilter写入），异步/MQ线程MDC为空则新生成
        //这样下游服务能继续用同一个traceId串联日志
        String traceId = TraceContextUtil.getTraceId();
        if (traceId == null || traceId.isEmpty()) {
            traceId = TraceContextUtil.begin();
        }
        requestTemplate.header(TraceContextUtil.HEADER_TRACE_ID, traceId);
    }
}
