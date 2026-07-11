package com.atguigu.tingshu.album.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.tingshu.album.mapper.*;
import com.atguigu.tingshu.album.service.BaseCategoryService;
import com.atguigu.tingshu.model.album.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
	//获取所有一级分类
	@Override
	public List<BaseCategory1> findAllCategory1() {
		return baseCategory1Mapper.selectList(null);
	}

	//根据一级分类id在视图base_category_view中查询下面所有二级和三级分类数据
	/*{
	一级分类id：  1 (categoryId)
	一级分类名称：音乐 (categoryName
	child: [ category2Child
		{
			二级分类id：11
			二级分类名称：音乐音效
			child: [ category3Child
				{
					三级分类id：111
					三级分类名称：运动音乐
				}
			]
		}
	]
}*/
	@Override
	public JSONObject getBaseCategoryListByCategory1Id(Long category1Id) {
		//1.根据一级分类ID查询视图base_category_view中相关数据
		LambdaQueryWrapper<BaseCategoryView> wrapper = new LambdaQueryWrapper<>();
		wrapper.eq(BaseCategoryView::getCategory1Id, category1Id);
		List<BaseCategoryView> baseCategoryViews = baseCategoryViewMapper.selectList(wrapper);
		//2.对该列表按照二级分类ID进行分组然后映射成map数组，key是二级分类ID，value是所属二级分类下的数据
		Map<Long, List<BaseCategoryView>> listMap = baseCategoryViews.stream().collect(Collectors.groupingBy(BaseCategoryView::getCategory2Id));
		//3.创建封装返回结果的JSON对象,将一级分类ID和一级分类名称封装进去
		JSONObject Category1 = new JSONObject();
		Category1.put("categoryId", category1Id);
		Category1.put("categoryName", baseCategoryViews.get(0).getCategory1Name());
		//创建一级分类下的二级分类集合
		List<JSONObject> category2Child = new ArrayList<>();
		//4.对map集合进行遍历，对一级分类下的二级分类集合进行封装，以及对二级分类下的三级分类集合进行封装
		listMap.forEach((key, value) -> {
			//创建封装二级分类的JSON对象
            JSONObject category2 = new JSONObject();
            //将二级分类ID和二级分类名称封装进去
            category2.put("categoryId", key);
            category2.put("categoryName", value.get(0).getCategory2Name());
            //创建二级分类下的三级分类集合
            List<JSONObject> category3Child = new ArrayList<>();
			//对二级分类下的三级分类集合进行封装
			value.forEach(item -> {
				JSONObject category3 = new JSONObject();
				category3.put("categoryId", item.getCategory3Id());
				category3.put("categoryName", item.getCategory3Name());
				category3Child.add(category3);
			});
			//将该二级分类下的三级分类集合加入到该二级分类的JSON对象中
			category2.put("categoryChild", category3Child);
			//将该二级分类的JSON对象加入到一级分类下的二级分类集合中
			category2Child.add(category2);
		});
		//将该一级分类下的二级分类集合加入到一级分类的JSON对象中
		Category1.put("categoryChild", category2Child);
		return Category1;
	}

	//根据一级分类Id 查询置顶频道页的三级分类列表
	@Override
	public List<BaseCategory3> selectTopBaseCategory3(Long category1Id) {

		//# 第一步 根据一级分类id查询下面所有二级分类id
		//SELECT bc.id FROM base_category2 bc WHERE bc.category1_id=1;
		//# 101  102  103
		LambdaQueryWrapper<BaseCategory2> wrapper2 = new LambdaQueryWrapper<>();
		wrapper2.eq(BaseCategory2::getCategory1Id, category1Id);
		List<BaseCategory2> baseCategory2List =
				baseCategory2Mapper.selectList(wrapper2);

		// baseCategory2List二级分类所有数据集合，获取所有二级分类id，构建新集合
		List<Long> category2IdList = baseCategory2List.stream()
				.map(BaseCategory2::getId)
				.collect(Collectors.toList());

		//# 第二步 根据第一步查询出来所有二级分类id，查询下面三级分类数据，
		//#有条件是否置顶 is_top=1,
		//SELECT * FROM base_category3 bc3 WHERE bc3.category2_id IN(101,102,103)
		//AND  bc3.is_top=1  LIMIT 7
		LambdaQueryWrapper<BaseCategory3> wrapper3 = new LambdaQueryWrapper<>();
		wrapper3.in(BaseCategory3::getCategory2Id, category2IdList);
		wrapper3.eq(BaseCategory3::getIsTop,1);
		wrapper3.last(" limit 7");
		//查询出置顶的三级分类数据
		List<BaseCategory3> baseCategory3List = baseCategory3Mapper.selectList(wrapper3);
		return baseCategory3List;
	}

	//根据三级分类ID在视图中查询一、二、三级分类信息
	@Override
	public BaseCategoryView getCategoryView(Long category3Id) {
		//因为base_category_view中的ID就是三级分类ID，所以可以直接根据三级分类ID查询一、二、三级分类信息
		BaseCategoryView baseCategoryView = baseCategoryViewMapper.selectById(category3Id);
		return baseCategoryView;
	}

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
	//根据一级分类ID获取对应的标签数据
	@Override
	public List<BaseAttribute> findAttribute(Long category1Id) {
//		调用mapper层编写sql语句实现查询
		return baseAttributeMapper.selectAttribute(category1Id);
	}
}
