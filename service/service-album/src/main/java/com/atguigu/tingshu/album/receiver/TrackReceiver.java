package com.atguigu.tingshu.album.receiver;

import com.alibaba.fastjson.JSON;
import com.atguigu.tingshu.album.service.TrackInfoService;
import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import com.atguigu.tingshu.common.util.TraceContextUtil;
import com.atguigu.tingshu.vo.album.TrackStatMqVo;
import com.rabbitmq.client.Channel;
import lombok.SneakyThrows;
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

@Component
public class TrackReceiver {

    @Autowired
    private TrackInfoService trackInfoService;

    @Autowired
    private RedisTemplate redisTemplate;
    //监听接收端，接收更新专辑和声音播放量的消息
    @SneakyThrows
    @RabbitListener(bindings = @QueueBinding(
            exchange = @Exchange(value = MqConst.EXCHANGE_TRACK, durable = "true"),
            value = @Queue(value = MqConst.QUEUE_TRACK_STAT_UPDATE, durable = "true"),
            key = {MqConst.ROUTING_TRACK_STAT_UPDATE}
    ))
    //处理发送的消息，
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
                //把字符串转换成指定类型的对象
                TrackStatMqVo trackStatMqVo = JSON.parseObject(content, TrackStatMqVo.class);
                if(trackStatMqVo != null) {
                    //1 获取发送端传递业务编号
                    String businessNo = trackStatMqVo.getBusinessNo();
                    //2 redis添加数据，使用setnx方法，带有锁机制
                    //为了保证RabbitMq消息幂等性，也就是因为网络问题可能导致消息被发送多次，但是只要求消费一次，使用Redis的setnx方法
                    //向Redis中存储，key为业务编号businessNo，value为1，设置过期时间为1小时的数据，这样第一次消费消息就添加的该条数据
                    //因为sexnx的锁机制，在没有主动使用del方法释放或者过期时间未到之前都无法对相同key的数据的value进行修改，这样后续的相同
                    //消息因为业务编号一样所以无法向Redis中设置相同key的数据，因此无法被消费
                    Boolean setIfAbsent = redisTemplate.opsForValue().setIfAbsent(businessNo, 1, 1, TimeUnit.HOURS);
                    //3 如果添加成功，证明第一次消费，就对播放量进行更新
                    if(setIfAbsent) {
                        //更新专辑和声音的播放量
                        trackInfoService.updateStat(trackStatMqVo);
                    }
                }
            }

            // 通过与队列的信道channel手动确认消息已成功处理，可以将其从队列中移除
            // basicAck是RabbitMQ中用于确认消息的方法
            // 参数1：deliveryTag，表示消息的唯一标识符
            // 参数2：multiple，是否批量确认，false表示只确认当前这条消息
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
        } finally {
            TraceContextUtil.end();
        }
    }
}
