package com.atguigu.tingshu.query.album;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "使用mysql搜索专辑的条件")
public class AlbumInfoQuery {

	@Schema(description = "专辑标题")
	private String albumTitle;

	@Schema(description = "专辑状态")
	private String status;

	@Schema(description = "用户id")
	private Long userId;
}