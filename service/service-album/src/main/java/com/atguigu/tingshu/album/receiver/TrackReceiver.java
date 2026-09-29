package com.atguigu.tingshu.album.receiver;

import com.alibaba.fastjson.JSON;
import com.atguigu.tingshu.album.service.TrackInfoService;
import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import com.atguigu.tingshu.common.util.TraceContextUtil;
import com.atguigu.tingshu.vo.album.TrackStatMqVo;
import com.rabbitmq.client.Channel;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

/**
 * 声音播放统计消息消费者。
 *
 * <p>播放量更新不阻塞用户播放请求，而是通过 MQ 异步落库。消费者用业务编号在 Redis 中做幂等控制，
 * 这样即使生产者重试或 RabbitMQ 重投，也不会重复累加统计值。</p>
 */
@Slf4j
@Component
public class TrackReceiver {

    @Autowired
    private TrackInfoService trackInfoService;

    @Autowired
    private RedisTemplate redisTemplate;
    /** 接收播放统计消息并更新专辑、声音的统计数据。 */
    @SneakyThrows
    @RabbitListener(bindings = @QueueBinding(
            exchange = @Exchange(value = MqConst.EXCHANGE_TRACK, durable = "true"),
            value = @Queue(value = MqConst.QUEUE_TRACK_STAT_UPDATE, durable = "true"),
            key = {MqConst.ROUTING_TRACK_STAT_UPDATE}
    ))
    /**
     * 更新统计信息的方法
     * @param content 消息内容，用于统计信息更新
     * @param message 消息对象，包含消息相关信息
     * @param channel 通道对象，用于通信和消息传递确认
     */
    public void updateStat(String content, Message message, Channel channel) {
        TraceContextUtil.begin(); //MQ消费无HTTP头，各自生成新traceId
        try {
            if(!StringUtils.isEmpty(content)) {
                // 生产者发送的是 JSON 文本，先还原成约定的消息对象。
                TrackStatMqVo trackStatMqVo = JSON.parseObject(content, TrackStatMqVo.class);
                if(trackStatMqVo != null) {
                    // businessNo 是消息幂等键，而不是数据库主键。
                    String businessNo = trackStatMqVo.getBusinessNo();
                    // setIfAbsent 等价于 Redis SETNX：只有第一次消费能写入标记。
                    Boolean setIfAbsent = redisTemplate.opsForValue().setIfAbsent(businessNo, 1, 1, TimeUnit.HOURS);
                    //3 如果添加成功，证明第一次消费，就对播放量进行更新
                    if(setIfAbsent) {
                        // 只有首次消费成功才允许累加统计值。
                        try {
                            trackInfoService.updateStat(trackStatMqVo);
                        } catch (Exception e) {
                            //【毒消息防护】统计为原子累加（幂等），重投无法恢复数据级失败，记日志后照常 ack
                            log.error("[播放统计] 更新失败，businessNo={}, albumId={}, trackId={}",
                                    businessNo, trackStatMqVo.getAlbumId(), trackStatMqVo.getTrackId(), e);
                        }
                    }
                }
            }

            // ack：业务失败已捕获并记日志，不无限重投（生产环境建议 DLX + 人工干预台）
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
        } finally {
            TraceContextUtil.end();
        }
    }
}
