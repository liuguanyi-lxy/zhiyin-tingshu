package com.atguigu.tingshu.user.client.impl;

import com.atguigu.tingshu.user.client.UserInfoFeignClient;
import com.atguigu.tingshu.user.client.impl.UserInfoDegradeFeignClient;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * UserInfoFeignClient 降级工厂：打印真实降级原因（见 AlbumInfoFeignClientFactory 说明）。
 */
@Component
public class UserInfoFeignClientFactory implements FallbackFactory<UserInfoFeignClient> {

    private static final Logger log = LoggerFactory.getLogger(UserInfoFeignClientFactory.class);

    private final UserInfoDegradeFeignClient delegate = new UserInfoDegradeFeignClient();

    @Override
    public UserInfoFeignClient create(Throwable cause) {
        log.warn("[Feign降级] service-user UserInfoFeignClient 调用失败，降级返回。", cause);
        return delegate;
    }
}
