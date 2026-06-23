package com.atguigu.tingshu.model.album;

import com.atguigu.tingshu.model.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;


@Data
@Schema(description = "后端用于保存专辑标签ID和标签值ID的实体类")
@TableName("album_attribute_value")
public class AlbumAttributeValue extends BaseEntity {

	private static final long serialVersionUID = 1L;

	@Schema(description = "专辑id")
	private Long albumId;

	@NotNull(message = "标签id不能为空")
	@Schema(description = "标签id")
	private Long attributeId;

	@NotNull(message = "标签值id不能为空")
	@Schema(description = "标签值id")
	private Long valueId;

	@Schema(description = "标签名称")
	@TableField(exist = false)
	private String attributeName;

	@Schema(description = "标签值")
	@TableField(exist = false)
	private String valueName;

}