package com.atguigu.tingshu.order.receiver;

import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import com.atguigu.tingshu.common.util.TraceContextUtil;
import com.atguigu.tingshu.order.service.OrderInfoService;
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

/**
 * 订单领域的 MQ 消费者。
 *
 * <p>支付服务确认成功后只发送订单号。订单服务收到消息后更新订单状态，并继续通知用户服务写入购买记录。
 * 手动 ack 确保业务处理完成后消息才从队列删除。</p>
 *
 * <p>【毒消息防护】业务失败时记录日志并照常 ack：订单状态推进本身是条件更新（幂等），
 * 重投无法恢复数据级失败，无限重投只会阻塞队列；生产环境建议加死信队列+人工干预台。</p>
 */
@Slf4j
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
                try {
                    orderInfoService.updateOrderSuccess(orderNo);
                } catch (Exception e) {
                    // 毒消息防护：失败消息不无限重投，记日志后照常 ack
                    log.error("[订单消费者] 支付成功处理失败，orderNo={}", orderNo, e);
                }
            }
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } finally {
            TraceContextUtil.end();
        }
    }
}
