package com.atguigu.tingshu.order.config;

import com.atguigu.tingshu.order.helper.SignHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;

/**
 * 订单签名密钥装配。
 *
 * <p>【修复#3】签名密钥原硬编码在 SignHelper 中并随仓库公开，现改为从配置中心读取：
 * Nacos 的 service-order 配置中增加 tingshu.order.sign-key 即可覆盖；
 * 未配置时使用占位默认值并打警告，生产环境必须配置。</p>
 */
@Slf4j
@Configuration
public class OrderSignKeyConfig {

    @Value("${tingshu.order.sign-key:}")
    private String signKey;

    @PostConstruct
    public void initSignKey() {
        if (StringUtils.hasText(signKey)) {
            SignHelper.setSignKey(signKey);
            log.info("[订单签名] 已从配置加载签名密钥");
        } else {
            log.warn("[订单签名] 未配置 tingshu.order.sign-key，正在使用占位默认密钥——仅可用于本地测试，生产环境必须在 Nacos 覆盖！");
        }
    }
}
