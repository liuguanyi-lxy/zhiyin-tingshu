package com.atguigu.tingshu.order.receiver;

import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import com.atguigu.tingshu.common.util.TraceContextUtil;
import com.atguigu.tingshu.order.service.OrderInfoService;
import com.rabbitmq.client.Channel;
import lombok.SneakyThrows;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 订单领域的 MQ 消费者。
 *
 * <p>支付服务确认成功后只发送订单号。订单服务收到消息后更新订单状态，并继续通知用户服务写入购买记录。
 * 手动 ack 确保业务处理完成后消息才从队列删除。</p>
 */
@Component
public class OrderReceiver {

    @Autowired
    private OrderInfoService orderInfoService;
    /** 消费支付成功消息，推进订单状态并生成用户购买记录。 */
    @SneakyThrows
    @RabbitListener(bindings = @QueueBinding(
            exchange = @Exchange(value = MqConst.EXCHANGE_ORDER, durable = "true"),
            value = @Queue(value = MqConst.QUEUE_ORDER_PAY_SUCCESS, durable = "true"),
            key = {MqConst.ROUTING_ORDER_PAY_SUCCESS}
    ))
    public void orderPaySuccess(String orderNo, Message message, Channel channel) {
        // MQ 消费没有 HTTP 请求头，需要为这条异步链路创建独立 traceId。
        try {
            if(StringUtils.hasText(orderNo)) {
                orderInfoService.updateOrderSuccess(orderNo);
            }
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } finally {
            TraceContextUtil.end();
        }
    }
}
