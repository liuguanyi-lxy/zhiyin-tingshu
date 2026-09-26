package com.atguigu.tingshu.common.login;

import com.atguigu.tingshu.common.constant.RedisConstant;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.model.user.UserInfo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * API 层登录校验切面。
 *
 * <p>网关已经做过一次 token 存在性校验，但服务内部仍保留这一层，原因是：
 * 服务可能被 Feign、定时任务或其他内部入口直接调用，不能只依赖网关；同时这里还负责建立
 * 当前请求的用户上下文。</p>
 */
@Component
@Aspect
@Slf4j
public class LoginAspect {
    @Autowired
    private RedisTemplate redisTemplate;
    /**
     * 校验 token，并在业务方法执行期间把用户 ID 放入 {@link AuthContextHolder}。
     *
     * <p>{@code required=false} 不是“完全不处理登录”，而是允许匿名访问；如果请求带有有效 token，
     * 仍然会建立用户上下文，方便同一个接口根据登录状态返回不同内容。</p>
     */
    @Around("execution(* com.atguigu.tingshu.*.api.*.*(..)) && @annotation(loginVerification)")
    public Object loginVerification(ProceedingJoinPoint joinPoint, LoginVerification loginVerification) throws Throwable {
        // Feign 或异步任务调用时可能没有 Servlet 请求上下文；API HTTP 请求通常一定存在。
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        log.info("拦截方法: {}, 注解: {}", joinPoint.getSignature().getName(), loginVerification);
        // 切点限定在 API Controller，因此这里可以取得当前 HTTP 请求。
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        // token 是前端和微服务之间约定的请求头名称。
        String token = request.getHeader("token");
        log.info("token:" + token);
        // required=true 时，token 缺失或 Redis 中没有用户对象都视为未登录。
        boolean required = loginVerification.required();
        if (required) {
            if (token == null) {
                throw new GuiguException(ResultCodeEnum.LOGIN_AUTH);
            }
            // 登录信息以 token 为 key 保存在 Redis 中。
            UserInfo userInfo =(UserInfo) redisTemplate.opsForValue().get(token);
            log.info("userInfo:" + userInfo);
            if(userInfo == null){
                throw new GuiguException(ResultCodeEnum.LOGIN_AUTH);
            }
        }
        // 即使接口允许匿名访问，只要 token 有效，也把用户 ID 放入当前线程，供业务层按需使用。
        if (!StringUtils.isEmpty(token)) {
                // 再读取一次用户对象，避免 required=false 时错误地写入无效用户 ID。
            UserInfo userInfo = (UserInfo) this.redisTemplate.opsForValue().get(token);
            if (null != userInfo) {
                AuthContextHolder.setUserId(userInfo.getId());
            }
        }
        try {
            // 登录校验通过后才进入真正的 Controller 方法。
            Object obj = joinPoint.proceed();
            return obj;
        } finally {
            // 必须在 finally 中清理 ThreadLocal，否则 Tomcat 线程池复用线程时会残留上一个请求的用户ID，
            // 导致后续未登录请求（如首页接口）错误地读到前一个用户的ID，造成越权访问或数据串号；
            // 同时 ThreadLocal 不清理会造成 Entry 与 Thread 强引用无法回收，引发内存泄漏。
            AuthContextHolder.removeUserId();
            AuthContextHolder.removeUsername();
        }
    }
}
