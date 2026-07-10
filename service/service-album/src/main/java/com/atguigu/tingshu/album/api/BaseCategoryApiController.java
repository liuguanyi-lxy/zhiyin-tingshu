package com.atguigu.tingshu.album.api;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.tingshu.album.service.BaseCategoryService;
import com.atguigu.tingshu.common.login.LoginVerification;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.album.BaseAttribute;
import com.atguigu.tingshu.model.album.BaseCategory3;
import com.atguigu.tingshu.model.album.BaseCategoryView;
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
	@LoginVerification
	@Operation(summary = "根据一级分类id查询对应的标签和标签值")
	@GetMapping("/findAttribute/{category1Id}")
	public Result findAttribute(@PathVariable("category1Id") Long category1Id){
		//调用baseCategoryService的方法实现
		List<BaseAttribute> baseAttributes = baseCategoryService.findAttribute(category1Id);
		return Result.ok(baseAttributes);
	}
	//远程调用：根据三级分类id在base_category_view视图中获取一、二、三级分类信息
	@Operation(summary = "通过三级分类id查询一、二、三级分类信息")
	@GetMapping("getCategoryView/{category3Id}")
	public Result<BaseCategoryView> getCategoryView(@PathVariable Long category3Id){
		//因为视图，id就是三级分类id
		BaseCategoryView baseCategoryView = baseCategoryService.getCategoryView(category3Id);
		return Result.ok(baseCategoryView);
	}
	/**
	 * 根据一级分类Id 查询置顶频道页的三级分类列表
	 * @param category1Id
	 * @return
	 */
	@Operation(summary = "获取一级分类下置顶到频道页的三级分类列表")
	@GetMapping("findTopBaseCategory3/{category1Id}")
	public Result<List<BaseCategory3>>
	findTopBaseCategory3(@PathVariable Long category1Id) {
		List<BaseCategory3> list = baseCategoryService.selectTopBaseCategory3(category1Id);
		return Result.ok(list);
	}
	/**
	 * 根据一级分类Id 获取该分类下所有分类的全部数据
	 * @param category1Id
	 * @return
	 */
	@Operation(summary = "根据一级分类id获取全部分类信息")
	@GetMapping("getBaseCategoryList/{category1Id}")
	public Result<JSONObject> getBaseCategoryList(@PathVariable Long category1Id){
		JSONObject jsonObject = baseCategoryService.getBaseCategoryListByCategory1Id(category1Id);
		return Result.ok(jsonObject);
	}
}

