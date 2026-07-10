package com.atguigu.tingshu.search.service;

import java.util.Map;

public interface ItemService {

    //根据专辑id查询专辑详情信息
    Map<String, Object> getItem(Long albumId);
}
