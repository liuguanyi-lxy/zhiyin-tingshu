package com.atguigu.tingshu.common.RedisCache.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RedisCache {
    /**
     * 缓存key的前缀
     * @return
     */
    String prefix() default "cache";
}
