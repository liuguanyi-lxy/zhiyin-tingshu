package com.atguigu.tingshu.common.interceptor;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Feign拦截器实现类，用于在Feign请求中传递token等自定义header信息
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
    }
}