package com.atguigu.tingshu.common.RedisCache.Aspect;

import com.atguigu.tingshu.common.RedisCache.annotation.RedisCache;
import com.atguigu.tingshu.common.constant.RedisConstant;
import lombok.SneakyThrows;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Aspect
@Component
public class RedisCacheAspect {
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private RedisTemplate redisTemplate;
    @SneakyThrows
    @Around("@annotation(com.atguigu.tingshu.common.RedisCache.annotation.RedisCache)")
    public Object RedisCache(ProceedingJoinPoint joinPoint) {
        //获取被增强方法的参数列表
        Object[] args = joinPoint.getArgs();
        //获取被增强方法,method对象封装了被增强方法的信息
        MethodSignature signature = (MethodSignature)joinPoint.getSignature();
        Method method = signature.getMethod();
        //获取被增强方法上指定注解的信息对象
        RedisCache redisCache = method.getAnnotation(RedisCache.class);
        //获取注解中的属性值
        String prefix = redisCache.prefix();
        //根据注解中的属性值和方法的参数组合成key
        String key = prefix + Arrays.toString(args);
        //查询Redis，根据key获取数据
        Object value = redisTemplate.opsForValue().get(key);
        //为了防止缓存击穿，就使用Redisson的分布式锁解决
        //如果查询结果为null说明缓存未命中，则加分布式锁，加锁成功就查询数据库，失败就自旋
        if (value == null) {
            // 使用Redisson获取分布式锁
            // 这里使用ALBUM_LOCK_SUFFIX作为锁的名称，用于相册相关的分布式锁控制
            RLock lock = redissonClient.getLock(RedisConstant.ALBUM_LOCK_SUFFIX);
            try {
                //尝试上锁，记录结果
                //想要看门狗机制生效就不能设置释放时间
                boolean res = lock.tryLock(3, TimeUnit.MINUTES);
                //上锁成功就查询mysql数据库，然后将数据添加到Redis缓存中
                if (res) {
                    //执行被增强方法从而完成查询数据库
                    Object result = joinPoint.proceed();
                    //为了防止缓存穿透，即使查询mysql数据库结果为null，也要设置空对象到缓存中,然后返回空对象
                    if (result == null) {
                        Object obj = new Object();
                        redisTemplate.opsForValue().set(key, obj, 5, TimeUnit.MINUTES);
                        return result;
                    } else {//查询mysql结果不为空，把查询数据添加到redis缓存中，然后返回查询结果
                        redisTemplate.opsForValue().set(key, result, 5, TimeUnit.MINUTES);
                        return result;
                    }
                } else {
                    //上锁失败，自旋,当第一个请求相同数据的线程完成从mysql数据库查询数据添加到Redis后，就可以直接从
                    //Redis中查询到数据
                    Thread.sleep(100);
                    //递归调用该通知方法，传入被增强方法的信息对象
                    return RedisCache(joinPoint);
                }
            } finally {
                //确保锁被释放
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        } else {
            //查询redis结果不为null，说明缓存命中，直接返回查询结果
            return value;
        }
    }
}
