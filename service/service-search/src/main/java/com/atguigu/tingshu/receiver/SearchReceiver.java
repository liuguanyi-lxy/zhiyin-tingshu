package com.atguigu.tingshu.receiver;

import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import com.atguigu.tingshu.search.service.SearchService;
import com.rabbitmq.client.Channel;
import lombok.SneakyThrows;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
//对RabbitMQ的监听器进行封装,监听消息队列中的消息
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
    @Retryable
    @RabbitListener(bindings = @QueueBinding(
            exchange = @Exchange(value = MqConst.EXCHANGE_ALBUM, durable = "true"),
            value = @Queue(value = MqConst.QUEUE_ALBUM_UPPER, durable = "true"),
            key = {MqConst.ROUTING_ALBUM_UPPER}
    ))
    public void upper_album(Long albumId, Message message, Channel channel) {
        //如果专辑ID不为空则进行向es中添加专辑信息
        if(albumId != null) {
            searchService.upperAlbum(albumId);
        }
        //手动确认，默认是自动确认
        // 确认消息已成功消费
        // basicAck方法用于确认消息已被消费者成功处理
        // 第一个参数deliveryTag表示消息的投递标签，用于唯一标识消息
        // 第二个参数multiple设为false表示仅确认当前消息，不确认之前所有消息
        channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
    }
    /**
     * 专辑下架
     *
     * @param albumId
     * @param message
     * @param channel
     */
    @SneakyThrows
    @Retryable
    @RabbitListener(bindings = @QueueBinding(
            exchange = @Exchange(value = MqConst.EXCHANGE_ALBUM, durable = "true"),
            value = @Queue(value = MqConst.QUEUE_ALBUM_LOWER, durable = "true"),
            key = {MqConst.ROUTING_ALBUM_LOWER}
    ))
    public void lower_album(Long albumId, Message message, Channel channel) {
        //业务处理
        if (null != albumId) {
            searchService.lowerAlbum(albumId);
        }
        //手动应答
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }
}
