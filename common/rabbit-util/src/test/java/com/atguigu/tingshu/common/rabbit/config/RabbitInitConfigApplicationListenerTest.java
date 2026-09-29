package com.atguigu.tingshu.common.rabbit.config;

import com.alibaba.fastjson.JSON;
import com.atguigu.tingshu.common.rabbit.entity.GuiguCorrelationData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 发布确认/返回回调单元测试。
 * 锁定【修复#1】：重试计数统一以 Redis 为准（confirm 内存对象与 returns 反序列化对象
 * 两条路径共享计数）；元数据缺失时跳过重试而不是 NPE；超过 3 次停止重发。
 */
@ExtendWith(MockitoExtension.class)
class RabbitInitConfigApplicationListenerTest {

    private static final String ID = "mq:test123";
    private static final String HEADER = "spring_returned_message_correlation";

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private RedisTemplate redisTemplate;

    @Mock
    private ValueOperations valueOperations;

    private RabbitTemplate.ConfirmCallback confirmCallback;
    private RabbitTemplate.ReturnsCallback returnsCallback;

    @BeforeEach
    void setUp() throws Exception {
        RabbitInitConfigApplicationListener listener = new RabbitInitConfigApplicationListener();
        inject(listener, "rabbitTemplate", rabbitTemplate);
        inject(listener, "redisTemplate", redisTemplate);

        ApplicationReadyEvent event = new ApplicationReadyEvent(
                new org.springframework.boot.SpringApplication(Object.class),
                new String[0], mock(org.springframework.context.ConfigurableApplicationContext.class), java.time.Duration.ZERO);
        listener.onApplicationEvent(event);

        ArgumentCaptor<RabbitTemplate.ConfirmCallback> confirmCaptor =
                ArgumentCaptor.forClass(RabbitTemplate.ConfirmCallback.class);
        ArgumentCaptor<RabbitTemplate.ReturnsCallback> returnsCaptor =
                ArgumentCaptor.forClass(RabbitTemplate.ReturnsCallback.class);
        verify(rabbitTemplate).setConfirmCallback(confirmCaptor.capture());
        verify(rabbitTemplate).setReturnsCallback(returnsCaptor.capture());
        confirmCallback = confirmCaptor.getValue();
        returnsCallback = returnsCaptor.getValue();
        // 部分用例不触碰 Redis，公共 stub 用 lenient 避免 UnnecessaryStubbing
        org.mockito.Mockito.lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    private void inject(Object target, String fieldName, Object value) throws Exception {
        Field field = RabbitInitConfigApplicationListener.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private String metadataJson(int retryCount) {
        GuiguCorrelationData data = new GuiguCorrelationData();
        data.setId(ID);
        data.setMessage("payload");
        data.setExchange("ex");
        data.setRoutingKey("rk");
        data.setRetryCount(retryCount);
        return JSON.toJSONString(data);
    }

    private ReturnedMessage returnedMessage(String correlationId) {
        MessageProperties properties = new MessageProperties();
        if (correlationId != null) {
            properties.setHeader(HEADER, correlationId);
        }
        return new ReturnedMessage(new Message("x".getBytes(), properties),
                312, "NO_ROUTE", "ex", "rk");
    }

    @Test
    void confirmSuccess_neverRetries() {
        confirmCallback.confirm(new GuiguCorrelationData(), true, null);

        verify(rabbitTemplate, never()).convertAndSend(org.mockito.ArgumentMatchers.<String>any(), org.mockito.ArgumentMatchers.<String>any(), org.mockito.ArgumentMatchers.<Object>any(), org.mockito.ArgumentMatchers.<CorrelationData>any());
    }

    @Test
    void confirmFailure_retriesAtMostThreeTimes() {
        // Redis 中计数依次为 0/1/2/3，模拟"每次重发后写回"的真实状态推进
        when(valueOperations.get(ID)).thenReturn(metadataJson(0), metadataJson(1), metadataJson(2), metadataJson(3));

        for (int i = 0; i < 4; i++) {
            GuiguCorrelationData data = new GuiguCorrelationData();
            data.setId(ID);
            confirmCallback.confirm(data, false, "boom");
        }

        verify(rabbitTemplate, times(3)).convertAndSend(eq("ex"), eq("rk"), org.mockito.ArgumentMatchers.<Object>any(), org.mockito.ArgumentMatchers.<CorrelationData>any());
        // 每次重发前计数写回 Redis
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations, times(3)).set(eq(ID), jsonCaptor.capture(), eq(10L), any(TimeUnit.class));
        assertEquals(1, JSON.parseObject(jsonCaptor.getAllValues().get(0), GuiguCorrelationData.class).getRetryCount());
        assertEquals(2, JSON.parseObject(jsonCaptor.getAllValues().get(1), GuiguCorrelationData.class).getRetryCount());
        assertEquals(3, JSON.parseObject(jsonCaptor.getAllValues().get(2), GuiguCorrelationData.class).getRetryCount());
    }

    @Test
    void returnsCallback_missingCorrelationId_skipsRetryWithoutNpe() {
        returnsCallback.returnedMessage(returnedMessage(null));

        verify(rabbitTemplate, never()).convertAndSend(org.mockito.ArgumentMatchers.<String>any(), org.mockito.ArgumentMatchers.<String>any(), org.mockito.ArgumentMatchers.<Object>any(), org.mockito.ArgumentMatchers.<CorrelationData>any());
        verify(valueOperations, never()).get(anyString());
    }

    @Test
    void returnsCallback_metadataLostInRedis_skipsRetryInsteadOfNpe() {
        //【修复#1】原实现 parseObject(null) 后直接 isDelay() 抛 NPE
        when(valueOperations.get(ID)).thenReturn(null);

        returnsCallback.returnedMessage(returnedMessage(ID));

        verify(rabbitTemplate, never()).convertAndSend(org.mockito.ArgumentMatchers.<String>any(), org.mockito.ArgumentMatchers.<String>any(), org.mockito.ArgumentMatchers.<Object>any(), org.mockito.ArgumentMatchers.<CorrelationData>any());
    }

    @Test
    void returnsCallback_routesFailure_retriesUsingRedisState() {
        when(valueOperations.get(ID)).thenReturn(metadataJson(0));

        returnsCallback.returnedMessage(returnedMessage(ID));

        verify(rabbitTemplate, times(1)).convertAndSend(eq("ex"), eq("rk"), org.mockito.ArgumentMatchers.<Object>any(), org.mockito.ArgumentMatchers.<CorrelationData>any());
        verify(valueOperations).set(eq(ID), anyString(), eq(10L), any(TimeUnit.class));
    }

    @Test
    void returnsCallback_delayMessage_skipsRetry() {
        GuiguCorrelationData delayData = JSON.parseObject(metadataJson(0), GuiguCorrelationData.class);
        delayData.setDelay(true);
        delayData.setDelayTime(15);
        when(valueOperations.get(ID)).thenReturn(JSON.toJSONString(delayData));

        returnsCallback.returnedMessage(returnedMessage(ID));

        verify(rabbitTemplate, never()).convertAndSend(org.mockito.ArgumentMatchers.<String>any(), org.mockito.ArgumentMatchers.<String>any(), org.mockito.ArgumentMatchers.<Object>any(), org.mockito.ArgumentMatchers.<CorrelationData>any());
        assertTrue(delayData.isDelay());
    }
}
