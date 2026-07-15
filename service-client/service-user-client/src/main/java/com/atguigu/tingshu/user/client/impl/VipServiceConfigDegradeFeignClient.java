package com.atguigu.tingshu.user.client.impl;


import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.user.VipServiceConfig;
import com.atguigu.tingshu.user.client.VipServiceConfigFeignClient;
import org.springframework.stereotype.Component;

/**
 * VIP服务配置降级Feign客户端实现类
 * 当VIP服务不可用时，此实现类将作为降级处理方案
 */
@Component
public class VipServiceConfigDegradeFeignClient implements VipServiceConfigFeignClient {

    /**
     * 获取VIP服务配置信息
     * @param id VIP服务配置ID
     * @return 返回VIP服务配置结果，当服务不可用时返回null
     */
    @Override
    public Result<VipServiceConfig> getVipServiceConfig(Long id) {
        return Result.fail();
    }
}
