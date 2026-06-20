package com.atguigu.tingshu.album.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.tingshu.album.mapper.*;
import com.atguigu.tingshu.album.service.BaseCategoryService;
import com.atguigu.tingshu.model.album.BaseAttribute;
import com.atguigu.tingshu.model.album.BaseCategory1;
import com.atguigu.tingshu.model.album.BaseCategoryView;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@SuppressWarnings({"all"})
public class BaseCategoryServiceImpl extends ServiceImpl<BaseCategory1Mapper, BaseCategory1> implements BaseCategoryService {

	@Autowired
	private BaseCategory1Mapper baseCategory1Mapper;

	@Autowired
	private BaseCategory2Mapper baseCategory2Mapper;

	@Autowired
	private BaseCategory3Mapper baseCategory3Mapper;


	@Autowired
	private BaseCategoryViewMapper baseCategoryViewMapper;

	@Autowired
	private BaseAttributeMapper baseAttributeMapper;
	//获取专辑所有分类
	/*前端接收json格式[
	{
		一级分类id：   1
		一级分类名称：音乐
		child: [
			{
				二级分类id：11
				二级分类名称：古典音乐
				child: [
					{
						三级分类id：111
						三级分类名称：希腊古典音乐
					}
				]
			}
		]
	},
	{
		一级分类id：   2
		一级分类名称：军事
	}
]*/
	@Override
	public List<JSONObject> getBaseCategoryList() {
		//1.将分类视图中的所有数据查询出来返回对应的List集合
		List<BaseCategoryView> baseCategoryViews = baseCategoryViewMapper.selectList(null);

		//创建List集合封装最终的三级分类嵌套结构
		List<JSONObject> list = new ArrayList<>();
		//3.将分类视图中的数据进行分组，分组条件是category1Id，将分组后的数据返回Map集合，key是一级分类ID，value是BaseCategoryView列表
		Map<Long, List<BaseCategoryView>> CategoryMap1 = baseCategoryViews
				.stream()
				.collect(Collectors.groupingBy(BaseCategoryView::getCategory1Id));
		//4.遍历一级分类的Map集合，使用JSONObject对象封装一级分类ID和一级分类名称以及对应的二级分类集合，使用JSONObject是因为其实现map接口
		//按照map的方式存储数据进行JSON序列化后可以方便转换成JSON格式
		CategoryMap1.forEach((key, value) -> {
			//获取一级分类ID和其分类下的二级分类集合
			Long category1Id = key;
			List<BaseCategoryView> CategoryList1 = value;
			//创建JSONObject对象封装一级分类ID和一级分类名称以及对应的二级分类集合
			JSONObject jsonObject1 = new JSONObject();
			jsonObject1.put("categoryId", category1Id);
			jsonObject1.put("categoryName", CategoryList1.get(0).getCategory1Name());
			//创建一级分类的孩子分类列表，存放该分类下的所用二级分类
			List<JSONObject> CategoryChild2 = new ArrayList<>();
			//将一级分类下的二级分类集合按照二级分类ID分组，分组条件是category1Id，将分组后的数据返回Map集合，key是二级分类ID，value是BaseCategoryView列表
			Map<Long, List<BaseCategoryView>> CategoryMap2 = CategoryList1
					.stream()
					.collect(Collectors.groupingBy(BaseCategoryView::getCategory2Id));
			//遍历二级分类的Map集合，使用JSONObject对象封装二级分类ID和二级分类名称以及对应的三级分类集合
			CategoryMap2.forEach((key2, value2) -> {
				//获取二级分类ID和其分类下的三级分类集合
                Long category2Id = key2;
                List<BaseCategoryView> CategoryList2 = value2;
				//创建JSONObject对象封装二级分类ID和二级分类名称以及对应的三级分类集合
				JSONObject jsonObject2 = new JSONObject();
                //创建JSONObject对象封装二级分类ID和二级分类名称以及对应的三级分类集合
                jsonObject2.put("categoryId", category2Id);
                jsonObject2.put("categoryName", CategoryList2.get(0).getCategory2Name());
				//对三级分类集合进行遍历，使用JSONObject对象封装三级分类ID和三级分类名称
				List<JSONObject> CategoryList3 = CategoryList2.stream().map(item -> {
					//创建JSONObject对象封装三级分类ID和三级分类名称
					JSONObject jsonObject3 = new JSONObject();
					jsonObject3.put("categoryId", item.getCategory3Id());
					jsonObject3.put("categoryName", item.getCategory3Name());
					return jsonObject3;
				}).collect(Collectors.toList());
				//把每个三级分类集合添加到二级分类对象中，作为分类孩子集合
				jsonObject2.put("categoryChild", CategoryList3);
				CategoryChild2.add(jsonObject2);
				});
			//把每个二级分类孩子集合添加到一级分类对象中，作为分类孩子集合
			jsonObject1.put("categoryChild", CategoryChild2);
			//把每个一级分类对象添加到一级分类集合中
			list.add(jsonObject1);
		});
		return list;
	}
}
