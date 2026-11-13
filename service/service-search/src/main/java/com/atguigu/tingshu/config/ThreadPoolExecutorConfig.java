package com.atguigu.tingshu.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
//创建一个配置类，将线程池作为Bean组件加入到Spring容器中，方便导入使用
@Configuration
public class ThreadPoolExecutorConfig {
    //使用ThreadPoolExecutor自定义创建一个线程池
    @Bean
    public ThreadPoolExecutor threadPoolExecutor(){
        ClassLoader classLoader = ThreadPoolExecutorConfig.class.getClassLoader();
        return new ThreadPoolExecutor(10,
                15,
                10, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(100),
                r -> {
                    Thread t = Executors.defaultThreadFactory().newThread(r);
                    t.setContextClassLoader(classLoader);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
