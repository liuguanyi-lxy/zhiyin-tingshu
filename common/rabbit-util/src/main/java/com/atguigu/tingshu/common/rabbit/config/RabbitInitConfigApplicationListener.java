package com.atguigu.tingshu.common.rabbit.config;

import com.alibaba.fastjson.JSON;
import com.atguigu.tingshu.common.rabbit.entity.GuiguCorrelationData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * RabbitInitConfigApplicationListener类实现Spring ApplicationListener接口，
 * 用于在Spring Boot应用启动后初始化RabbitMQ的消息确认和返回回调配置
 */
@Slf4j
@Component
public class RabbitInitConfigApplicationListener implements ApplicationListener<ApplicationReadyEvent> {

    /** 单条消息的最大重发次数 */
    private static final int MAX_RETRY = 3;

    /** 与 RabbitService 的元数据 TTL 保持一致 */
    private static final long METADATA_TTL_MINUTES = 10;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RedisTemplate redisTemplate;

/**
 * 重写onApplicationEvent方法，在Spring Boot应用程序完全启动后执行回调设置
 * @param event 应用程序就绪事件，当应用程序已准备好接收请求时触发
 */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
    // 调用setupCallbacks方法设置回调
        this.setupCallbacks();
    }

    /**
     * 设置RabbitMQ的消息的确认confirm和返回return回调
     * 包含两个主要回调：
     * 1. confirm 确认回调 - 检查消息是否成功到达Exchange
     * 2. return 返回回调 - 处理消息未正确到达队列的情况
     */
    private void setupCallbacks() {

        /**
         * 只确认消息是否正确到达 Exchange 中,成功与否都会回调
         *
         * @param correlation 相关数据  非消息本身业务数据，发送消息时就会存入本地内存中
         * @param ack             broker返回的confirm应答结果
         * @param reason           如果发送消息到交换器失败，错误原因
         */
        this.rabbitTemplate.setConfirmCallback((correlationData, ack, reason) -> {
            if (ack) {
                //消息到交换器成功
                log.info("消息发送到Exchange成功：{}", correlationData);
            } else {
                //消息到交换器失败
                log.error("消息发送到Exchange失败：{}", reason);

                //执行消息重发
                this.retrySendMsg(correlationData);
            }
        });

        /**
         * 消息没有正确到达队列时触发回调，如果正确到达队列不执行
         */
        this.rabbitTemplate.setReturnsCallback(returned -> {
            log.error("Returned: " + returned.getMessage() + "\nreplyCode: " + returned.getReplyCode()
                    + "\nreplyText: " + returned.getReplyText() + "\nexchange/rk: "
                    + returned.getExchange() + "/" + returned.getRoutingKey());

            //当路由队列失败 也需要重发
            //1.从消息头里拿回 correlation id，再到 Redis 找回元数据
            String redisKey = returned.getMessage().getMessageProperties().getHeader("spring_returned_message_correlation");
            //【修复#1】元数据缺失（Redis 过期/丢失/先发后存竞态）时原实现会 parseObject(null) 后 NPE；
            // 现在跳过重试并记日志，交由对账兜底
            if (!StringUtils.hasText(redisKey)) {
                log.error("[消息重试] 返回消息缺少 correlation id，无法重试，exchange/rk: {}/{}",
                        returned.getExchange(), returned.getRoutingKey());
                return;
            }
            this.retrySendMsg(redisKey);
        });
    }

    /**
     * 消息重新发送。
     *
     * <p>【修复#1】重试状态统一以 Redis 为准：confirm 回调拿到的是内存对象、returns 回调是从
     * Redis 反序列化的对象，两条路径如果各自维护计数会分叉；现统一按 id 从 Redis 读最新
     * retryCount，递增后立即写回，两条路径共享同一份计数。</p>
     *
     * @param correlationIdOrData confirm 路径传 CorrelationData（取其 id），returns 路径直接传 id
     */
    private void retrySendMsg(CorrelationData correlationData) {
        retrySendMsg(correlationData == null ? null : correlationData.getId());
    }

    private void retrySendMsg(String correlationId) {
        if (!StringUtils.hasText(correlationId)) {
            return;
        }
        String correlationDataStr = (String) redisTemplate.opsForValue().get(correlationId);
        if (!StringUtils.hasText(correlationDataStr)) {
            log.error("[消息重试] 重试元数据已丢失（Redis 过期/丢失），放弃重试，id={}，请通过对账任务兜底", correlationId);
            return;
        }
        GuiguCorrelationData gmallCorrelationData = JSON.parseObject(correlationDataStr, GuiguCorrelationData.class);
        if (gmallCorrelationData.getRetryCount() >= MAX_RETRY) {
            //超过最大重试次数
            log.error("[消息重试] 生产者超过最大重试次数，id={}，请人工处理", correlationId);
            return;
        }
        //重发次数+1 并立即写回 Redis（先写回再重发，重发过程再次失败时计数不丢）
        gmallCorrelationData.setRetryCount(gmallCorrelationData.getRetryCount() + 1);
        redisTemplate.opsForValue().set(gmallCorrelationData.getId(),
                JSON.toJSONString(gmallCorrelationData), METADATA_TTL_MINUTES, java.util.concurrent.TimeUnit.MINUTES);
        log.info("[消息重试] 第 {} 次重发，id={}", gmallCorrelationData.getRetryCount(), gmallCorrelationData.getId());
        //重发消息：延迟消息重发时必须重新设置延迟时间，否则会被立即消费
        if (gmallCorrelationData.isDelay()) {
            //延迟消息
            rabbitTemplate.convertAndSend(gmallCorrelationData.getExchange(), gmallCorrelationData.getRoutingKey(), gmallCorrelationData.getMessage(), message -> {
                message.getMessageProperties().setDelay(gmallCorrelationData.getDelayTime() * 1000);
                return message;
            }, gmallCorrelationData);
        } else {
            //普通消息
            rabbitTemplate.convertAndSend(gmallCorrelationData.getExchange(), gmallCorrelationData.getRoutingKey(), gmallCorrelationData.getMessage(), gmallCorrelationData);
        }
    }

}
