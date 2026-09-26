package com.atguigu.tingshu.common.RedisCache.Aspect;

import com.atguigu.tingshu.common.RedisCache.annotation.RedisCache;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
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
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 缓存注解切面：负责「先查缓存 -> 未命中回源数据库 -> 回填缓存」的通用逻辑。
 *
 * 针对缓存三大问题分别做了处理：
 *  1. 缓存穿透：数据库查不到时缓存一个空对象（短 TTL），防止同一个不存在的 key 反复打库；
 *  2. 缓存击穿：使用 Redisson 分布式锁，只放一个线程回源，其余线程等待后直接读缓存；
 *  3. 缓存雪崩：TTL 在基础值上叠加随机抖动，避免大量 key 同时过期。
 */
@Aspect
@Component
@Slf4j
public class RedisCacheAspect {

    /** Redis 中用于表示数据库空结果的稳定占位值。 */
    private enum NullCacheValue {
        INSTANCE
    }

    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private RedisTemplate redisTemplate;

    /** 缓存基础过期时间（秒） */
    private static final long BASE_EXPIRE_SECONDS = 5 * 60;
    /** 空结果缓存时间（秒），避免不存在数据长期占用缓存并允许新数据及时可见 */
    private static final long NULL_EXPIRE_SECONDS = 30;
    /** TTL 随机抖动上限（秒），用于打散过期时间，防止缓存雪崩 */
    private static final long RANDOM_JITTER_SECONDS = 60;
    /** 获取锁的最大等待时间（秒） */
    private static final long LOCK_WAIT_SECONDS = 3;
    /** 自旋重试的最大次数，避免无限递归导致栈溢出 */
    private static final int MAX_RETRY = 3;

    /**
     * 缓存增强入口：先查 Redis，只有缓存未命中时才进入分布式锁和数据库回源流程。
     */
    @Around("@annotation(com.atguigu.tingshu.common.RedisCache.annotation.RedisCache)")
    public Object redisCache(ProceedingJoinPoint joinPoint) throws Throwable {
        //获取被增强方法的参数列表
        Object[] args = joinPoint.getArgs();
        //获取被增强方法,method对象封装了被增强方法的信息
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        //获取被增强方法上指定注解的信息对象
        RedisCache redisCache = method.getAnnotation(RedisCache.class);
        //获取注解中的属性值
        String prefix = redisCache.prefix();
        //根据注解中的属性值和方法的参数组合成key
        String key = prefix + Arrays.toString(args);

        //1. 先查缓存，命中直接返回，不参与任何加锁
        Object value = redisTemplate.opsForValue().get(key);
        if (value != null) {
            return value == NullCacheValue.INSTANCE ? null : value;
        }

        //2. 缓存未命中，进入"加锁回源"流程（最多重试 MAX_RETRY 次，替代原来的无限递归）
        return loadWithLock(joinPoint, key, 0);
    }

    /**
     * 加锁回源。锁的粒度是「当前 key」，不同 key 之间互不阻塞。
     *
     * @param retryCount 当前已重试次数，达到上限后降级为直接查库，保证请求不会失败
     */
    private Object loadWithLock(ProceedingJoinPoint joinPoint, String key, int retryCount) throws Throwable {
        //锁名与缓存 key 一一对应，避免所有缓存共抢一把锁导致串行化
        RLock lock = redissonClient.getLock(key + ":lock");
        boolean locked = false;
        try {
            //尝试获取锁，最多等待 LOCK_WAIT_SECONDS 秒；不设置租约时间，交给 Redisson 看门狗自动续期
            locked = lock.tryLock(LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                //3. 没抢到锁：说明已经有线程在回源，稍等后重新读缓存（自旋）
                if (retryCount >= MAX_RETRY) {
                    //重试次数用尽（例如持锁线程回源异常耗时过长），降级为直接查库，避免请求被无限挂起
                    log.warn("获取分布式锁重试 {} 次仍未成功，降级直接查询数据库, key={}", MAX_RETRY, key);
                    return joinPoint.proceed();
                }
                Thread.sleep(ThreadLocalRandom.current().nextLong(30, 100));
                //二次检查：持锁线程可能已经把数据写进缓存了，此时无需再加锁
                Object cached = redisTemplate.opsForValue().get(key);
                if (cached != null) {
                    return cached == NullCacheValue.INSTANCE ? null : cached;
                }
                return loadWithLock(joinPoint, key, retryCount + 1);
            }

            //4. 抢到锁后必须再做一次二次检查：前一个持锁线程可能刚回源完成并释放了锁，
            //   此时缓存已有数据，直接返回即可，避免重复查库（Double Check）
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return cached == NullCacheValue.INSTANCE ? null : cached;
            }

            //5. 回源数据库
            Object result = joinPoint.proceed();
            //过期时间 = 基础值 + 随机抖动，防止雪崩
            if (result == null) {
                //6. 防穿透：使用可识别的字符串占位，并采用明显更短的 TTL
                redisTemplate.opsForValue().set(key, NullCacheValue.INSTANCE,
                        NULL_EXPIRE_SECONDS, TimeUnit.SECONDS);
                return null;
            }
            long expire = BASE_EXPIRE_SECONDS + ThreadLocalRandom.current().nextLong(RANDOM_JITTER_SECONDS);
            redisTemplate.opsForValue().set(key, result, expire, TimeUnit.SECONDS);
            return result;
        } finally {
            //只释放当前线程自己持有的锁，避免释放别人的锁导致锁语义失效
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
