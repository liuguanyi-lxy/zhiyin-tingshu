package com.atguigu.tingshu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
//当应用启动时，@EnableDiscoveryClient 会自动配置并启动服务发现相关的组件
//它会创建一个服务注册实例，该实例负责将当前服务的信息（如服务名、IP地址、端口号等）注册到配置的服务注册中心
//同时，它会创建一个服务发现客户端，用于从注册中心获取已注册的服务列表
@EnableDiscoveryClient
/**
 * 启用Feign客户端的注解
 * 该注解用于开启Spring Cloud Feign的支持，使应用程序能够使用Feign客户端进行声明式RESTful服务调用
 * 当添加此注解到Spring Boot配置类上时，Spring容器会扫描并注册所有带有@FeignClient注解的接口
 *
 * @author Spring Framework
 * @see org.springframework.cloud.netflix.feign.EnableFeignClients
 * @since 1.0
 */
@EnableFeignClients
public class ServiceAlbumApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceAlbumApplication.class, args);
    }

}
