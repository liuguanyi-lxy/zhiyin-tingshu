package com.atguigu.tingshu.common.rabbit.entity;

import lombok.Data;
import org.springframework.amqp.rabbit.connection.CorrelationData;

/**
 * 自定义关联数据类，继承自CorrelationData
 * 用于存储消息发送的相关信息，包括消息体、交换机、路由键等
 */
@Data
public class GuiguCorrelationData extends CorrelationData {

    //消息体，存储实际需要发送的消息内容
    private Object message;
    //交换机名称，指定消息发送到哪个交换机
    private String exchange;
    //路由键，用于确定消息的路由规则
    private String routingKey;
    //重试次数，记录消息发送失败后的重试次数，默认为0
    private int retryCount = 0;
    //是否延迟消息
    private boolean isDelay = false;
    //延迟时长
    private int delayTime = 10;
}
