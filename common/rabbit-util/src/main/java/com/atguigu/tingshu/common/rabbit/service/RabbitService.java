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
        // correlationData 不是消费者的业务消息，而是发布确认阶段识别和重试消息所需的元数据。
        GuiguCorrelationData correlationData = new GuiguCorrelationData();
        String uuid = "mq:" + UUID.randomUUID().toString().replaceAll("-", "");
        correlationData.setId(uuid);
        correlationData.setMessage(message);
        correlationData.setExchange(exchange);
        correlationData.setRoutingKey(routingKey);
        // RabbitTemplate 负责把业务消息投递给 exchange，再由 routingKey 路由到队列。
        rabbitTemplate.convertAndSend(exchange, routingKey, message, correlationData);

        // 发布确认回调需要根据 id 找回原消息，因此把元数据短暂保存到 Redis。
        redisTemplate.opsForValue().set(uuid, JSON.toJSONString(correlationData), 10, TimeUnit.MINUTES);
        return true;
    }

    /**
     * 发送延迟消息。
     * @param exchange 交换机
     * @param routingKey 路由键
     * @param message 消息数据
     * @param delayTime 延迟时间，单位为：秒
     */
    public boolean sendDealyMessage(String exchange, String routingKey, Object message, int delayTime) {
        // 延迟消息与普通消息使用同一套发布确认机制，只是额外记录延迟属性。
        GuiguCorrelationData correlationData = new GuiguCorrelationData();
        String uuid = "mq:" + UUID.randomUUID().toString().replaceAll("-", "");
        correlationData.setId(uuid);
        correlationData.setMessage(message);
        correlationData.setExchange(exchange);
        correlationData.setRoutingKey(routingKey);
        correlationData.setDelay(true);
        correlationData.setDelayTime(delayTime);

        // RabbitMQ 延迟插件使用毫秒值，因此对外的秒数参数需要转换成毫秒。
        rabbitTemplate.convertAndSend(exchange, routingKey, message,message1 -> {
            message1.getMessageProperties().setDelay(delayTime*1000);
            return message1;
        }, correlationData);

        // 延迟消息也必须保留在 Redis 中，避免发布确认失败后无法重试。
        redisTemplate.opsForValue().set(uuid, JSON.toJSONString(correlationData), 10, TimeUnit.MINUTES);
        return true;

    }

}
