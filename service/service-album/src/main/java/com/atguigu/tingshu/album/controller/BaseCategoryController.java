package com.atguigu.tingshu.album.controller;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.tingshu.album.service.BaseCategoryService;
import com.atguigu.tingshu.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 *
 * 
 *
 */
/**
 * 分类管理接口控制器
 * 该注解用于标识该控制器属于"分类管理"模块
 */
@Tag(name = "分类管理")
@RestController
@RequestMapping(value="/api/album/category")
@SuppressWarnings("all")  // 使用单行注释，表示抑制所有警告
public class BaseCategoryController {
	
	@Autowired
	private BaseCategoryService baseCategoryService;
	//查询专辑所有分类，总共三级分类，嵌套结构
	@Operation(summary = "查询专辑所有分类")
    @GetMapping("getBaseCategoryList")
    public Result getBaseCategoryList() {
		List<JSONObject> AlbumCategorylist = baseCategoryService.getBaseCategoryList();
		return Result.ok(AlbumCategorylist);
	}
}

