package com.atguigu.tingshu.album.service;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.tingshu.model.album.BaseAttribute;
import com.atguigu.tingshu.model.album.BaseCategory1;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface BaseCategoryService extends IService<BaseCategory1> {
    //获取所有分类
    List<JSONObject> getBaseCategoryList();
    //根据一级分类id获取标签数据
    List<BaseAttribute> findAttribute(Long category1Id);
}
