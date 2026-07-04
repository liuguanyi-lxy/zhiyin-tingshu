package com.atguigu.tingshu.user.config;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.api.impl.WxMaServiceImpl;
import cn.binarywang.wx.miniapp.config.impl.WxMaDefaultConfigImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

/**
 * 微信公众号配置类
 * 用于配置和初始化微信公众号相关的服务
 */
@Component
public class WeChatMpConfig {

    /**
     * 自动注入微信账号配置对象
     * 包含了微信公众号的appId和appSecret等配置信息
     */
    @Autowired
    private WechatAccountConfig wechatAccountConfig;

    /**
     * 创建并配置微信公众号服务
     * 该方法通过Spring的@Bean注解将WxMaService实例注入到Spring容器中

     * 配置包括设置应用ID、密钥和消息数据格式
     *
     * @return 返回配置好的WxMaService实例，用于后续的微信服务调用
     */
    @Bean
    public WxMaService wxMaService(){
        //  创建对象,配置好应用ID和秘钥
        WxMaDefaultConfigImpl wxMaConfig =  new WxMaDefaultConfigImpl();
        wxMaConfig.setAppid(wechatAccountConfig.getAppId());
        wxMaConfig.setSecret(wechatAccountConfig.getAppSecret());
        wxMaConfig.setMsgDataFormat("JSON");

        //  创建 WxMaService 对象
        WxMaService service = new WxMaServiceImpl();
        //  给 WxMaService 设置配置选项
        service.setWxMaConfig(wxMaConfig);
        return service;
    }
}
