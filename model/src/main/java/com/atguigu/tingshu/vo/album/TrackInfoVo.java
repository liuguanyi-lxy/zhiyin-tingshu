package com.atguigu.tingshu.vo.album;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
@Schema(description = "前端添加和修改声音传递给后端的声音信息")
public class TrackInfoVo {
	//@Positive 是 Java Bean Validation (JSR 380) 规范中的一个注解，通常在 Spring Boot 项目中配合 javax.validation 或 jakarta.validation 包使用。
	//它的主要作用是校验被标注的数值必须严格大于 0（即 > 0）。
	@Positive(message = "专辑Id必须大于0")
	@Schema(description = "专辑id", required = true)
	private Long albumId;

	@NotEmpty(message = "媒体文件Id不能为空")
	@Schema(description = "媒体文件的唯一标识", required = true)
	private String mediaFileId;

	@NotEmpty(message = "声音标题不能为空")
	@Schema(description = "声音标题", required = true)
	private String trackTitle;

	@Schema(description = "声音简介")
	private String trackIntro;

	@Schema(description = "声音简介，富文本")
	private String trackRichIntro;

	@Schema(description = "声音封面图url")
	private String coverUrl;

	@Schema(description = "是否公开：0-否 1-是")
	private String isOpen;

}