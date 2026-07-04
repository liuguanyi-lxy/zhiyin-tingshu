package com.atguigu.tingshu.model.search;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Data
@Schema(description = "用作es中存储专辑标签数据")
public class AttributeValueIndex {
	@Schema(description = "标签id")
	@Field(type = FieldType.Long)
	private Long attributeId;
	@Schema(description = "标签名称id")
	@Field(type = FieldType.Long)
	private Long valueId;

}