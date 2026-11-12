package com.atguigu.tingshu.user.sms;

/** 短信发送边界：便于测试替换，不在业务代码里直接依赖具体云厂商。 */
public interface SmsClient {

    void sendCode(String phone, String code);
}
