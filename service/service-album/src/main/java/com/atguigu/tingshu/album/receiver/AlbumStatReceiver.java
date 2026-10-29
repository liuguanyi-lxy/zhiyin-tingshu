package com.atguigu.tingshu.album.receiver;

import com.alibaba.fastjson.JSON;
import com.atguigu.tingshu.album.service.AlbumInfoService;
import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import com.atguigu.tingshu.common.util.TraceContextUtil;
import com.atguigu.tingshu.vo.album.AlbumStatMqVo;
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
 * 专辑统计变更消息消费者。
 *
 * <p>评论数等统计由业务方（评论服务）在动作完成后异步投递到这里，避免统计更新阻塞主流程。
 * 消费端用 businessNo 做 setIfAbsent 幂等，重复投递不会重复累加。</p>
 */
@Slf4j
@Component
public class AlbumStatReceiver {

	@Autowired
	private AlbumInfoService albumInfoService;

	@Autowired
	private RedisTemplate redisTemplate;

	@SneakyThrows
	@RabbitListener(bindings = @QueueBinding(
			exchange = @Exchange(value = MqConst.EXCHANGE_ALBUM, durable = "true"),
			value = @Queue(value = MqConst.QUEUE_ALBUM_STAT_UPDATE, durable = "true"),
			key = {MqConst.ROUTING_ALBUM_STAT_UPDATE}
	))
	public void updateAlbumStat(String content, Message message, Channel channel) {
		TraceContextUtil.begin(); //MQ消费无HTTP头，各自生成新traceId
		try {
			if (!StringUtils.isEmpty(content)) {
				AlbumStatMqVo statMqVo = JSON.parseObject(content, AlbumStatMqVo.class);
				if (statMqVo != null && statMqVo.getAlbumId() != null && StringUtils.hasText(statMqVo.getBusinessNo())) {
					// setIfAbsent 等价 SETNX：只有第一次消费能写入标记
					Boolean first = redisTemplate.opsForValue().setIfAbsent(statMqVo.getBusinessNo(), 1, 1, TimeUnit.HOURS);
					if (Boolean.TRUE.equals(first)) {
						try {
							albumInfoService.updateStat(statMqVo.getAlbumId(), statMqVo.getStatType(), statMqVo.getCount());
						} catch (Exception e) {
							// 业务失败（如统计行不存在、无符号列下溢）只记日志并 ack，避免毒消息无限重投
							log.error("[专辑统计] 更新失败，businessNo={}, albumId={}, statType={}",
									statMqVo.getBusinessNo(), statMqVo.getAlbumId(), statMqVo.getStatType(), e);
						}
					}
				}
			}
			channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
		} finally {
			TraceContextUtil.end();
		}
	}
}
