package com.atguigu.tingshu.common.rabbit.service;

import com.alibaba.fastjson.JSON;
import com.atguigu.tingshu.common.rabbit.entity.GuiguCorrelationData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RabbitService 单元测试。
 * 锁定【修复#1】：元数据先落 Redis 再发送（confirm 回调由 broker 连接线程异步执行，
 * 先发后存会读到空元数据）；延迟消息发布时把秒转毫秒塞进延迟头。
 */
@ExtendWith(MockitoExtension.class)
class RabbitServiceTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private RedisTemplate redisTemplate;

    @Mock
    private ValueOperations valueOperations;

    private RabbitService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new RabbitService();
        inject("rabbitTemplate", rabbitTemplate);
        inject("redisTemplate", redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private void inject(String fieldName, Object value) throws Exception {
        Field field = RabbitService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(service, value);
    }

    @Test
    void sendMessage_writesMetadataToRedisBeforePublishing() {
        service.sendMessage("ex", "rk", "payload");

        InOrder inOrder = inOrder(valueOperations, rabbitTemplate);
        inOrder.verify(valueOperations).set(anyString(), anyString(), eq(10L), any(TimeUnit.class));
        inOrder.verify(rabbitTemplate).convertAndSend(eq("ex"), eq("rk"), eq("payload"), any(GuiguCorrelationData.class));
    }

    @Test
    void sendMessage_metadataContainsExchangeRoutingAndPayload() {
        service.sendMessage("ex", "rk", "payload");

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(anyString(), jsonCaptor.capture(), eq(10L), any(TimeUnit.class));
        GuiguCorrelationData data = JSON.parseObject(jsonCaptor.getValue(), GuiguCorrelationData.class);
        assertEquals("ex", data.getExchange());
        assertEquals("rk", data.getRoutingKey());
        assertEquals("payload", data.getMessage());
        assertEquals(0, data.getRetryCount());
        assertTrue(data.getId().startsWith("mq:"));
    }

    @Test
    void sendDelayMessage_storesDelayMetadataAndSetsDelayHeader() throws Exception {
        service.sendDelayMessage("ex", "rk", "payload", 15);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(anyString(), jsonCaptor.capture(), eq(10L), any(TimeUnit.class));
        GuiguCorrelationData data = JSON.parseObject(jsonCaptor.getValue(), GuiguCorrelationData.class);
        assertTrue(data.isDelay());
        assertEquals(15, data.getDelayTime());

        // 发布时带 MessagePostProcessor，把秒转毫秒塞进延迟头
        ArgumentCaptor<MessagePostProcessor> mppCaptor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate).convertAndSend(eq("ex"), eq("rk"), eq("payload"), mppCaptor.capture(), any(GuiguCorrelationData.class));
        Message message = new Message("body".getBytes(), new MessageProperties());
        mppCaptor.getValue().postProcessMessage(message);
        assertEquals(15 * 1000, message.getMessageProperties().getDelay());
    }
}
