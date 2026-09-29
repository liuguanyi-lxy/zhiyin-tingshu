package com.atguigu.tingshu.receiver;

import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import com.atguigu.tingshu.common.util.TraceContextUtil;
import com.atguigu.tingshu.search.service.SearchService;
import com.rabbitmq.client.Channel;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
//对RabbitMQ的监听器进行封装,监听消息队列中的消息
/**
 * 专辑上/下架消息消费者：同步 ES 索引。
 *
 * <p>【修复#2】原方法上的 @Retryable 无效——工程未开 @EnableRetry，且它标注在
 * MQ 监听器方法上（由容器直接调用，代理不生效），属于应付式写法，已移除。
 * 失败处理统一为毒消息防护模式：记日志后照常 ack，索引偏差由上架事件重放/全量重建兜底。</p>
 */
@Slf4j
@Component
public class SearchReceiver {

    @Autowired
    private SearchService searchService;
    /**
     * 专辑上架
     *
     * @param albumId
     * @param message
     * @param channel
     */
    @SneakyThrows
    @RabbitListener(bindings = @QueueBinding(
            exchange = @Exchange(value = MqConst.EXCHANGE_ALBUM, durable = "true"),
            value = @Queue(value = MqConst.QUEUE_ALBUM_UPPER, durable = "true"),
            key = {MqConst.ROUTING_ALBUM_UPPER}
    ))
    public void upper_album(Long albumId, Message message, Channel channel) {
        TraceContextUtil.begin(); //MQ消费无HTTP头，各自生成新traceId
        try {
            //如果专辑ID不为空则进行向es中添加专辑信息
            if(albumId != null) {
                try {
                    searchService.upperAlbum(albumId);
                } catch (Exception e) {
                    //【毒消息防护】ES 写失败重投无法恢复（数据仍在），记日志后照常 ack
                    log.error("[搜索消费者] 专辑上架同步 ES 失败，albumId={}", albumId, e);
                }
            }
            //手动确认，默认是自动确认
            // 确认消息已成功消费
            // basicAck方法用于确认消息已被消费者成功处理
            // 第一个参数deliveryTag表示消息的投递标签，用于唯一标识消息
            // 第二个参数multiple设为false表示仅确认当前消息，不确认之前所有消息
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
        } finally {
            TraceContextUtil.end();
        }
    }
    /**
     * 专辑下架
     *
     * @param albumId
     * @param message
     * @param channel
     */
    @SneakyThrows
    @RabbitListener(bindings = @QueueBinding(
            exchange = @Exchange(value = MqConst.EXCHANGE_ALBUM, durable = "true"),
            value = @Queue(value = MqConst.QUEUE_ALBUM_LOWER, durable = "true"),
            key = {MqConst.ROUTING_ALBUM_LOWER}
    ))
    public void lower_album(Long albumId, Message message, Channel channel) {
        TraceContextUtil.begin(); //MQ消费无HTTP头，各自生成新traceId
        try {
            //业务处理
            if (null != albumId) {
                try {
                    searchService.lowerAlbum(albumId);
                } catch (Exception e) {
                    //【毒消息防护】同上
                    log.error("[搜索消费者] 专辑下架同步 ES 失败，albumId={}", albumId, e);
                }
            }
            //手动应答
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } finally {
            TraceContextUtil.end();
        }
    }
}
