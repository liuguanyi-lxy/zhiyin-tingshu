package com.atguigu.tingshu.album.client.impl;

import com.atguigu.tingshu.album.client.CategoryFeignClient;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * CategoryFeignClient 降级工厂：打印真实降级原因（见 {@link AlbumInfoFeignClientFactory} 说明）。
 */
@Component
public class CategoryFeignClientFactory implements FallbackFactory<CategoryFeignClient> {

    private static final Logger log = LoggerFactory.getLogger(CategoryFeignClientFactory.class);

    private final CategoryDegradeFeignClient delegate = new CategoryDegradeFeignClient();

    @Override
    public CategoryFeignClient create(Throwable cause) {
        log.warn("[Feign降级] service-album CategoryFeignClient 调用失败，降级返回。", cause);
        return delegate;
    }
}
