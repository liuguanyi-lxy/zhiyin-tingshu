package com.atguigu.tingshu.account.receiver;

import com.atguigu.tingshu.account.service.UserAccountService;
import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import com.rabbitmq.client.Channel;
import lombok.SneakyThrows;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
//在service-account模块编写接收端，获取发送过来userId，初始化账户信息
public class AccountReceiver {

    @Autowired
    private UserAccountService userAccountService;
    //这是 Lombok 提供的注解，用于自动将代码中的受检异常（如 IOException，basicAck 方法会抛出该异常）
    // 转换为非受检异常抛出，从而省去了繁琐的 try-catch 代码块。
    @SneakyThrows
    @RabbitListener(bindings = @QueueBinding(
            exchange = @Exchange(value = MqConst.EXCHANGE_USER, durable = "true"),
            value = @Queue(value = MqConst.QUEUE_USER_REGISTER, durable = "true"),
            key = {MqConst.ROUTING_USER_REGISTER}
    ))
    public void receive(Long userId, Message message, Channel channel) {
        if(userId != null) {
            userAccountService.addUserAccount(userId);
        }
        //告诉 RabbitMQ 该消息已经成功处理，可以将其从队列中删除。false：表示不批量确认，仅确认当前这条消息
        // Channel是RabbitMQ 原生的通道，主要用于手动确认消息
        channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
    }
}
