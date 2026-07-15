package com.atguigu.tingshu.common.config.redisson;

import lombok.Data;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Redisson配置类
 * 使用@Configuration和@ConfigurationProperties注解来配置Redisson连接参数
 * 配置前缀为"spring.data.redis"
 */
@Data
@Configuration
//模块间共享配置：
//即使当前模块没有配置文件，只要同级模块或父模块的配置文件位于 Spring Boot 能够扫描到的位置，仍然可以读取到配置。
@ConfigurationProperties("spring.data.redis")
public class RedissonConfig {
    private String host;      // Redis服务器主机地址

    private String password;  // Redis服务器密码

    private String port;      // Redis服务器端口

    private int timeout = 3000;  // 连接超时时间，默认为3000毫秒
    private static String ADDRESS_PREFIX = "redis://";  // Redis连接地址前缀

    /**
     * 自动装配RedissonClient Bean
     * 配置单机版Redisson连接
     * @return 返回配置好的RedissonClient实例
     * @throws RuntimeException 当host为空时抛出运行时异常
     */
    @Bean
    RedissonClient redissonSingle() {
        Config config = new Config();  // 创建Redisson配置对象

        // 检查host是否为空，为空则抛出异常
        if(StringUtils.isEmpty(host)){
            throw new RuntimeException("host is  empty");
        }
        // 配置单机服务器连接参数
        SingleServerConfig serverConfig = config.useSingleServer()
                .setAddress(ADDRESS_PREFIX + this.host + ":" + port)  // 设置服务器地址
                .setTimeout(this.timeout);  // 设置连接超时时间
        // 如果密码不为空，则设置密码
        if(!StringUtils.isEmpty(this.password)) {
            serverConfig.setPassword(this.password);
        }
        return Redisson.create(config);  // 创建并返回RedissonClient实例
    }
}
