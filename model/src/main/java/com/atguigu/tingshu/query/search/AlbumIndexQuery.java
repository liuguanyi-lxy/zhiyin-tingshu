package com.atguigu.tingshu.query.search;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "使用ES搜索专辑信息的条件")
public class AlbumIndexQuery {

	@Schema(description = "关键字")
	private String keyword;

	@Schema(description = "一级分类")
	private Long category1Id;

	@Schema(description = "二级分类")
	private Long category2Id;

	@Schema(description = "三级分类")
	private Long category3Id;

	@Schema(description = "标签（标签id:标签值id）")
	private List<String> attributeList;

	// order=1:asc  排序规则   0:asc
	@Schema(description = "排序（综合排序[1:desc] 播放量[2:desc] 发布时间[3:desc]；asc:升序 desc:降序）")
	private String order = "";// 1：综合排序 2：播放量 3：最近更新

	private Integer pageNo = 1;//分页信息
	private Integer pageSize = 10;
}