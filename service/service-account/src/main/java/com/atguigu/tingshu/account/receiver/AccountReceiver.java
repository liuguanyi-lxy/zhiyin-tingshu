package com.atguigu.tingshu.account.receiver;

import com.atguigu.tingshu.account.service.RechargeInfoService;
import com.atguigu.tingshu.account.service.UserAccountService;
import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import com.atguigu.tingshu.common.util.TraceContextUtil;
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
import org.springframework.util.StringUtils;

@Component
@Slf4j
//在service-account模块编写接收端，获取发送过来userId，初始化账户信息
public class AccountReceiver {

    @Autowired
    private UserAccountService userAccountService;
    @Autowired
    private RechargeInfoService rechargeInfoService;
    //这是 Lombok 提供的注解，用于自动将代码中的受检异常（如 IOException，basicAck 方法会抛出该异常）
    // 转换为非受检异常抛出，从而省去了繁琐的 try-catch 代码块。
    @SneakyThrows
    @RabbitListener(bindings = @QueueBinding(
            exchange = @Exchange(value = MqConst.EXCHANGE_USER, durable = "true"),
            value = @Queue(value = MqConst.QUEUE_USER_REGISTER, durable = "true"),
            key = {MqConst.ROUTING_USER_REGISTER}
    ))
    public void receive(Long userId, Message message, Channel channel) {
        TraceContextUtil.begin(); //MQ消费无HTTP头，各自生成新traceId
        try {
            if(userId != null) {
                try {
                    userAccountService.addUserAccount(userId);
                } catch (Exception e) {
                    // 毒消息防护：初始化失败重投无法恢复，记日志后照常 ack
                    log.error("[账户消费者] 用户账户初始化失败，userId={}", userId, e);
                }
            }
            //告诉 RabbitMQ 该消息已经成功处理，可以将其从队列中删除。false：表示不批量确认，仅确认当前这条消息
            // Channel是RabbitMQ 原生的通道，主要用于手动确认消息
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
        } finally {
            TraceContextUtil.end();
        }
    }

    /**
     * 接收来自微信支付成功后，异步调用发送到account微服务的充值成功通知，对充值记录状态和用户账户进行修改
     * @param orderNo
     * @param message
     * @param channel
     */
    @SneakyThrows
    @RabbitListener(bindings = @QueueBinding(
            exchange = @Exchange(value = MqConst.EXCHANGE_ORDER, durable = "true"),
            value = @Queue(value = MqConst.QUEUE_RECHARGE_PAY_SUCCESS, durable = "true"),
            key = {MqConst.ROUTING_RECHARGE_PAY_SUCCESS}
    ))
    public void rechargePaySuccess(String orderNo, Message message, Channel channel) {
        TraceContextUtil.begin(); //MQ消费无HTTP头，各自生成新traceId
        try {
            log.info("充值成功通知: {}", orderNo);
            //业务处理
            if (StringUtils.hasText(orderNo)){
                try {
                    //通知更新用户账号
                    rechargeInfoService.rechargePaySuccess(orderNo);
                } catch (Exception e) {
                    // 毒消息防护：充值入账是条件更新（幂等），重投无法恢复数据级失败，记日志后照常 ack
                    log.error("[账户消费者] 充值入账失败，orderNo={}", orderNo, e);
                }
            }

            //手动应答
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } finally {
            TraceContextUtil.end();
        }
    }
}
