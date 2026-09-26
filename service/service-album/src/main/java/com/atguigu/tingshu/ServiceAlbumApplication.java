package com.atguigu.tingshu;

import com.atguigu.tingshu.album.service.AlbumInfoService;
import com.atguigu.tingshu.common.constant.RedisConstant;
import com.atguigu.tingshu.model.album.AlbumInfo;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

import java.util.List;

/**
 * 专辑服务启动类。
 *
 * <p>除了启动 Spring Boot、注册 Nacos 服务和扫描 Feign 客户端外，启动完成后还会初始化专辑 ID 布隆过滤器。
 * 该初始化让专辑详情查询可以在访问缓存和数据库前快速过滤随机不存在的 ID。</p>
 */
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
//实现CommandLineRunner接口，重写run方法，在Spring Boot应用启动后执行一些初始化操作
public class ServiceAlbumApplication implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(ServiceAlbumApplication.class);
    
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private AlbumInfoService albumInfoService;
    public static void main(String[] args) {
        SpringApplication.run(ServiceAlbumApplication.class, args);
    }
    /**
     * 应用启动后加载现有专辑 ID。
     *
     * <p>布隆过滤器允许少量“误判存在”，但不会把真实存在的 ID 判断为不存在；因此未命中的 ID 可以安全地直接拒绝。</p>
     */
    public void run(String... args) throws Exception {
        logger.info("开始初始化布隆过滤器...");
        //初始化布隆过滤器,并指定布隆过滤器的名称
        RBloomFilter<Object> bloomFilter = redissonClient.getBloomFilter(RedisConstant.ALBUM_BLOOM_FILTER);
        //设置数据规模 误判率 预计统计元素数量为100000，期望误差率为0.01
        bloomFilter.tryInit(100000, 0.01);
        logger.info("布隆过滤器初始化完成，开始从数据库加载专辑ID...");
        //将数据库中所有专辑的ID添加到布隆过滤器中
        List<AlbumInfo> albumInfoList = albumInfoService.list();
        logger.info("从数据库加载到 {} 个专辑", albumInfoList.size());
        
        for (AlbumInfo albumInfo : albumInfoList) {
            //将专辑的ID加入到布隆过滤器中
            bloomFilter.add(albumInfo.getId());
        }
        
        logger.info("所有专辑ID已添加到布隆过滤器中");
    }
}
