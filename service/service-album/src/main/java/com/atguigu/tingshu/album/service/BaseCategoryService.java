package com.atguigu.tingshu.album.service;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.tingshu.model.album.BaseAttribute;
import com.atguigu.tingshu.model.album.BaseCategory1;
import com.atguigu.tingshu.model.album.BaseCategory3;
import com.atguigu.tingshu.model.album.BaseCategoryView;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface BaseCategoryService extends IService<BaseCategory1> {
    //获取所有分类
    List<JSONObject> getBaseCategoryList();
    //根据一级分类id获取标签数据
    List<BaseAttribute> findAttribute(Long category1Id);
    ////根据三级分类id获取一、二、三级分类信息
    BaseCategoryView getCategoryView(Long category3Id);
    //根据一级分类Id 查询置顶频道页的三级分类列表
    List<BaseCategory3> selectTopBaseCategory3(Long category1Id);
    //根据一级分类Id 获取该分类下所有二、三级分类的数据
    JSONObject getBaseCategoryListByCategory1Id(Long category1Id);
}
