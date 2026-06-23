package com.atguigu.tingshu.vo.album;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "前端传给后端关于专辑的标签ID和标签值ID")
public class AlbumAttributeValueVo {

	@NotNull(message = "标签id不能为空")
	@Schema(description = "标签id")
	private Long attributeId;

	@NotNull(message = "标签值id不能为空")
	@Schema(description = "标签值id")
	private Long valueId;

}