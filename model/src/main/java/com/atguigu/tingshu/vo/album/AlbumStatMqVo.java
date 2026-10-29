package com.atguigu.tingshu.vo.album;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 专辑统计变更消息（EXCHANGE_ALBUM / ROUTING_ALBUM_STAT_UPDATE）。
 * 生产者（如评论服务）在业务动作后发送，album 服务消费并原子累加 album_stat。
 */
@Data
@Schema(description = "专辑统计变更消息")
public class AlbumStatMqVo implements Serializable {

	private static final long serialVersionUID = 1L;

	@Schema(description = "专辑id")
	private Long albumId;

	@Schema(description = "统计类型：0401播放 0402订阅 0403购买 0404评论")
	private String statType;

	@Schema(description = "增量（可为负数，表示扣减）")
	private Integer count;

	@Schema(description = "业务幂等号，消费端以 setIfAbsent 防重复累加")
	private String businessNo;
}
