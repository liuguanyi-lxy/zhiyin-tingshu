package com.atguigu.tingshu.common.rabbit.service;


import com.alibaba.fastjson.JSON;
import com.atguigu.tingshu.common.rabbit.entity.GuiguCorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * RabbitMQ 发送门面。
 *
 * <p>业务服务只需要传入 exchange、routing key 和业务消息，不需要重复处理 RabbitTemplate 细节。
 * 每条消息还会以 correlation id 暂存到 Redis，供发布确认失败后的重试机制使用。</p>
 */
@Service
public class RabbitService {

    /** 确认元数据在 Redis 中的保留时长：足够覆盖重试窗口，过期后放弃重试交由人工/对账兜底 */
    private static final long METADATA_TTL_MINUTES = 10;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 发送普通消息。
     * @param exchange 交换机
     * @param routingKey 路由键
     * @param message 消息
     */
    public boolean sendMessage(String exchange, String routingKey, Object message) {
        GuiguCorrelationData correlationData = buildCorrelationData(exchange, routingKey, message, null);
        //【修复#1】先落 Redis 再发送：confirm 回调由 broker 连接线程异步执行，
        // 原实现"先发后存"，确认极快时回调读不到元数据导致无法重试（甚至 NPE）。
        redisTemplate.opsForValue().set(correlationData.getId(), JSON.toJSONString(correlationData), METADATA_TTL_MINUTES, TimeUnit.MINUTES);
        // RabbitTemplate 负责把业务消息投递给 exchange，再由 routingKey 路由到队列。
        rabbitTemplate.convertAndSend(exchange, routingKey, message, correlationData);
        return true;
    }

    /**
     * 发送延迟消息。
     * @param exchange 交换机
     * @param routingKey 路由键
     * @param message 消息数据
     * @param delayTime 延迟时间，单位为：秒
     */
    public boolean sendDelayMessage(String exchange, String routingKey, Object message, int delayTime) {
        GuiguCorrelationData correlationData = buildCorrelationData(exchange, routingKey, message, delayTime);
        redisTemplate.opsForValue().set(correlationData.getId(), JSON.toJSONString(correlationData), METADATA_TTL_MINUTES, TimeUnit.MINUTES);
        // RabbitMQ 延迟插件使用毫秒值，因此对外的秒数参数需要转换成毫秒。
        rabbitTemplate.convertAndSend(exchange, routingKey, message, message1 -> {
            message1.getMessageProperties().setDelay(delayTime * 1000);
            return message1;
        }, correlationData);
        return true;
    }

/**
 * 构建用于消息队列的相关数据对象
 * @param exchange 交换机名称
 * @param routingKey 路由键
 * @param message 要发送的消息内容
 * @param delayTime 延迟时间（毫秒），如果为null则不设置延迟
 * @return 返回构建好的GuiguCorrelationData对象
 */
    private GuiguCorrelationData buildCorrelationData(String exchange, String routingKey, Object message, Integer delayTime) {
        // correlationData 不是消费者的业务消息，而是发布确认阶段识别和重试消息所需的元数据。
        GuiguCorrelationData correlationData = new GuiguCorrelationData();
        // 生成一个唯一的UUID标识符，并移除其中的连字符
        String uuid = "mq:" + UUID.randomUUID().toString().replaceAll("-", "");
        // 设置消息的唯一标识ID
        correlationData.setId(uuid);
        // 设置要发送的消息内容
        correlationData.setMessage(message);
        // 设置消息交换机名称
        correlationData.setExchange(exchange);
        // 设置消息的路由键
        correlationData.setRoutingKey(routingKey);
        // 如果指定了延迟时间，则设置延迟相关属性
        if (delayTime != null) {
            // 标记为延迟消息
            correlationData.setDelay(true);
            // 设置延迟时间
            correlationData.setDelayTime(delayTime);
        }
        return correlationData;
    }

}
