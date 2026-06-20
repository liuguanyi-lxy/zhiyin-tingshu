package com.atguigu.tingshu.album.api;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.tingshu.album.service.BaseCategoryService;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.album.BaseAttribute;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@Tag(name = "分类管理")
@RestController
@RequestMapping(value="/api/album/category")
@SuppressWarnings({"all"})
public class BaseCategoryApiController {
	
	@Autowired
	private BaseCategoryService baseCategoryService;
	@Operation(summary = "根据一级分类id查询对应的标签和标签值")
	@GetMapping("/findAttribute/{category1Id}")
	public Result findAttribute(@PathVariable("category1Id") Long category1Id){
		//调用baseCategoryService的方法实现
		List<BaseAttribute> baseAttributes = baseCategoryService.findAttribute(category1Id);
		return Result.ok(baseAttributes);
	}
}

