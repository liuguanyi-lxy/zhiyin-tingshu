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

@Component
@Aspect//设置该类为切面类
@Slf4j
public class LoginAspect {
    @Autowired
    private RedisTemplate redisTemplate;
    //登录校验通知方法
    //ProceedingJoinPoint表示被增强的方法信息和被增强方法的执行
    //该环绕注解指定该通知方法增强的方法和使用什么注解时候被增强
    //该切面类在Spring事务切面类的内部，所以出现的异常要手动抛出，不然外部无法检测异常
    //loginVerification表示被增强的方法上使用的注解对象
    //&&表示两个条件必须同时满足。即：被拦截的方法既要在指定的 api 包路径下，方法上又必须添加了指定的登录校验注解，两者缺一不可。
    @Around("execution(* com.atguigu.tingshu.*.api.*.*(..)) && @annotation(loginVerification)")
    public Object loginVerification(ProceedingJoinPoint joinPoint, LoginVerification loginVerification) throws Throwable {
        //想要获取被增强的接口的请求参数可以通过RequestContextHolder获取request对象
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        log.info("拦截方法: {}, 注解: {}", joinPoint.getSignature().getName(), loginVerification);
        //获取request对象
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        //1.获取请求头中的token，该token应该是前端传递过来的
        String token = request.getHeader("token");
        log.info("token:" + token);
        //2.根据token查询redis数据库，查询用户信息
        //如果可以查询到则用户已经登录，可以继续执行方法，否则用户登录超时，返回错误信息，提示用户登录
        //因为loginVerification注解有属性 required
        //这个属性 required==true表示必须登录，required==false可以登录可以不登录（比如首页面）
        boolean required = loginVerification.required();
        if (required) {
            //表示必须登录
            //判断token是否为空，如果为空则返回错误信息，提示用户登录
            if (token == null) {
                throw new GuiguException(ResultCodeEnum.LOGIN_AUTH);
            }
            //token不为空，则查询redis数据库，查询用户信息
            UserInfo userInfo =(UserInfo) redisTemplate.opsForValue().get(token);
            log.info("userInfo:" + userInfo);
            //如果Redis查询不到用户信息则说明登录超时了，需要重新登录
            if(userInfo == null){
                throw new GuiguException(ResultCodeEnum.LOGIN_AUTH);
            }
        }
        //一些后端接口对应的任务需要用户ID，就在调用接口之前的登录校验方法时，将用户ID存储到本地线程中！
        //用户登录后，前端会请求后端接口服务的请求头中的token不为空，就将将用户Id存储到本地线程中！ 便于后台微服务获取用户Id！
        if (!StringUtils.isEmpty(token)){
            //  获取缓存中用户数据
            UserInfo userInfo = (UserInfo) this.redisTemplate.opsForValue().get(token);
            if (null != userInfo){
                //存储用户Id到本地线程，用于后端微服务获取用户ID,但是localThread存活时间为一次请求，所以想要在获取用户信息的接口获取用户ID就得添加登录校验注解
                AuthContextHolder.setUserId(userInfo.getId());
            }
        }
        //3.执行被增强的方法
        Object obj = joinPoint.proceed();
        return obj;
    }
}
