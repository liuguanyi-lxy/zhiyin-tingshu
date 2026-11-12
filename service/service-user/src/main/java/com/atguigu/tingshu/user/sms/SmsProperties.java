package com.atguigu.tingshu.user.sms;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 阿里云短信配置。默认关闭；未配置 AccessKey/签名/模板时不会发出任何真实短信。
 */
@Data
@Component
@ConfigurationProperties(prefix = "tingshu.sms")
public class SmsProperties {

    /** 总开关：只有显式开启且四项凭证齐全才会真实发送 */
    private boolean enabled = false;
    private String accessKeyId;
    private String accessKeySecret;
    private String signName;
    private String templateCode;
    private String regionId = "cn-hangzhou";
    private String endpoint = "https://dysmsapi.aliyuncs.com";
    /** 验证码有效期（秒） */
    private int codeTtlSeconds = 300;
    /** 同一手机号重发间隔（秒） */
    private int resendSeconds = 60;
    /** 单条验证码最多校验次数 */
    private int maxAttempts = 5;
    /** 同一手机号每日最多发送次数 */
    private int dailyLimit = 10;

    public boolean isConfigured() {
        return enabled
                && accessKeyId != null && !accessKeyId.isBlank()
                && accessKeySecret != null && !accessKeySecret.isBlank()
                && signName != null && !signName.isBlank()
                && templateCode != null && !templateCode.isBlank();
    }
}
