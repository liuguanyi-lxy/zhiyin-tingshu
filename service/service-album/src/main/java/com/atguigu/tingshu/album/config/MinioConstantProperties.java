package com.atguigu.tingshu.album.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Minio配置类
 * 用于读取配置文件中以"minio"为前缀的配置项，并将其配置属性映射到类的对应字段属性中
 */
@Configuration // 标记为配置类
@ConfigurationProperties(prefix="minio") //读取节点
@Data
public class MinioConstantProperties {
    // Minio服务端地址
    private String endpointUrl;
    //账户 名
    private String accessKey;
    //账户 密码
    private String secreKey;
    //存储桶名称
    private String bucketName;
}
