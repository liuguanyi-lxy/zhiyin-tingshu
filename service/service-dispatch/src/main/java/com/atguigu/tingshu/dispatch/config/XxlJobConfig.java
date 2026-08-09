package com.atguigu.tingshu.dispatch.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * xxl-job config
 *
 * @author xuxueli 2017-04-28
 */
/**
 * XXL-JOB配置类
 * 用于配置和初始化XXL-JOB分布式任务调度平台的执行器
 */
@Configuration
public class XxlJobConfig {
    // 日志记录器
    private Logger logger = LoggerFactory.getLogger(XxlJobConfig.class);

    // 从配置文件中注入XXL-JOB调度中心部署地址 [如: http://127.0.0.1:8080/xxl-job-admin]
    @Value("${xxl.job.admin.addresses}")
    private String adminAddresses;

    // 调度通讯TOKEN [选填]：非空时启用
    @Value("${xxl.job.accessToken}")
    private String accessToken;

    // 执行器AppName [选填]：执行器心跳分组依据；为空关闭
    @Value("${xxl.job.executor.appname}")
    private String appname;

    // 执行器 [选填]：使用该执行器的地址，为空关闭
    @Value("${xxl.job.executor.address}")
    private String address;

    // 执行器IP [选填]：多网卡时可设置IP
    @Value("${xxl.job.executor.ip}")
    private String ip;

    // 执行器端口号 [选填]：<小于等于0则自动获取；大于0使用固定端口
    @Value("${xxl.job.executor.port}")
    private int port;

    // 执行器运行日志文件路径 [选填]：配置路径为/logPath/xxl-job/jobhandler
    @Value("${xxl.job.executor.logpath}")
    private String logPath;

    @Value("${xxl.job.executor.logretentiondays}")
    private int logRetentionDays;


    @Bean
    public XxlJobSpringExecutor xxlJobExecutor() {
        logger.info(">>>>>>>>>>> xxl-job config init.");
        XxlJobSpringExecutor xxlJobSpringExecutor = new XxlJobSpringExecutor();
        xxlJobSpringExecutor.setAdminAddresses(adminAddresses);
        xxlJobSpringExecutor.setAppname(appname);
        xxlJobSpringExecutor.setAddress(address);
        xxlJobSpringExecutor.setIp(ip);
        xxlJobSpringExecutor.setPort(port);
        xxlJobSpringExecutor.setAccessToken(accessToken);
        xxlJobSpringExecutor.setLogPath(logPath);
        xxlJobSpringExecutor.setLogRetentionDays(logRetentionDays);

        return xxlJobSpringExecutor;
    }
}
