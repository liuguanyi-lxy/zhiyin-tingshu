package com.atguigu.tingshu.album.client.impl;

import com.atguigu.tingshu.album.client.AlbumInfoFeignClient;
import com.atguigu.tingshu.common.result.Result;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * AlbumInfoFeignClient 降级工厂：打印真实降级原因。
 * <p>【联调修复】原 fallback=AlbumInfoDegradeFeignClient 静默吞掉调用异常，线上只能看到
 * "专辑信息不存在"的断言失败，无法区分实例不可达/超时/熔断。改为 fallbackFactory 后
 * 每次降级都会把 cause 记入 WARN 日志，降级返回值与原降级类保持一致。</p>
 */
@Component
public class AlbumInfoFeignClientFactory implements FallbackFactory<AlbumInfoFeignClient> {

    private static final Logger log = LoggerFactory.getLogger(AlbumInfoFeignClientFactory.class);

    private final AlbumInfoDegradeFeignClient delegate = new AlbumInfoDegradeFeignClient();

    @Override
    public AlbumInfoFeignClient create(Throwable cause) {
        // 传 throwable 以打印完整堆栈，定位降级根因（如 LoadBalancer 子上下文解析失败）
        log.warn("[Feign降级] service-album AlbumInfoFeignClient 调用失败，降级返回。", cause);
        return delegate;
    }
}
