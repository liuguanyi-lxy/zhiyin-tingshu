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
 * Feign拦截器实现类，用于在Feign请求中传递token、traceId等自定义header信息
 * 该类实现了RequestInterceptor接口，会在Feign发送请求前拦截并处理请求
 */
@Component
public class FeignInterceptor implements RequestInterceptor {

    /**
     * 实现RequestInterceptor接口的apply方法，用于拦截并修改Feign请求
     * @param requestTemplate Feign请求模板，可以添加、修改请求头等信息
     */
    public void apply(RequestTemplate requestTemplate){
        //  获取调用端的请求对象
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        //异步编排 与 MQ消费者端 为 null
        if(null != requestAttributes) {
            ServletRequestAttributes servletRequestAttributes = (ServletRequestAttributes)requestAttributes;
            HttpServletRequest request = servletRequestAttributes.getRequest();
            //获取调用端的请求request中请求头中key为token的值
            String token = request.getHeader("token");
            //将token添加到Feign请求的请求头中,这样token就会被传递到被调用的微服务对应的接口中
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