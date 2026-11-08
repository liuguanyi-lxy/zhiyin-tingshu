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
 * <p>签名密钥从 Nacos 或环境变量读取；缺失、空白或过短时阻止服务启动。</p>
 */
@Slf4j
@Configuration
public class OrderSignKeyConfig {

    @Value("${tingshu.order.sign-key:}")
    private String signKey;

    @PostConstruct
    public void initSignKey() {
        if (!StringUtils.hasText(signKey) || signKey.length() < SignHelper.MIN_SIGN_KEY_LENGTH) {
            throw new IllegalStateException("tingshu.order.sign-key must contain at least 32 characters");
        }
        SignHelper.setSignKey(signKey);
        log.info("[订单签名] 已从配置加载签名密钥");
    }
}
