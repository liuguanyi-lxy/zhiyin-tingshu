package com.atguigu.tingshu.model.album;

import com.atguigu.tingshu.model.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "分类下标签名称和对应的标签值")
@TableName("base_attribute")
public class BaseAttribute extends BaseEntity {

	private static final long serialVersionUID = 1L;

	@Schema(description = "一级分类id")
	@TableField("category1_id")
	private Long category1Id;

	@Schema(description = "标签显示名称,比如:有声书分类")
	@TableField("attribute_name")
	private String attributeName;
	@Schema(description = "标签名称下对应的标签值集合,比如[男频小说，女频小说]")
	@TableField(exist = false)
	private List<BaseAttributeValue> attributeValueList;
}