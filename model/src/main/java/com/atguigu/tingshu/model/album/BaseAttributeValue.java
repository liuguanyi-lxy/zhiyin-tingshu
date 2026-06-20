package com.atguigu.tingshu.model.album;

import com.atguigu.tingshu.model.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "标签对应的标签值")
@TableName("base_attribute_value")
public class BaseAttributeValue extends BaseEntity {

	private static final long serialVersionUID = 1L;

	@Schema(description = "标签id")
	@TableField("attribute_id")
	private Long attributeId;

	@Schema(description = "标签值")
	@TableField("value_name")
	private String valueName;

}