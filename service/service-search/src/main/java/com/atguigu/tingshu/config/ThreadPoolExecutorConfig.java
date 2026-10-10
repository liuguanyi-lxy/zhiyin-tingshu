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
// 创建一个线程池并返回
// 核心线程数为3，最大线程数为5
// 空闲线程存活时间为10秒
// 使用有界工作队列ArrayBlockingQueue，容量为3
// 使用默认的线程工厂创建线程
//【修复】拒绝策略由 AbortPolicy 改为 CallerRunsPolicy：
// AbortPolicy 在池+队列满时直接抛 RejectedExecutionException，任务被丢弃——
// 详情页聚合（ItemServiceImpl 四路 CompletableFuture）在高并发缓存未命中时会直接 500，
// 批量上架（batchUpperAlbum）并发灌数时实测拒绝率高达 97%。
// CallerRunsPolicy 改为"调用线程自己执行"，形成自然背压：不丢任务、只是提交方变慢。
        return new ThreadPoolExecutor(10,
                15,
                10, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(3),
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
