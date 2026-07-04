package com.atguigu.tingshu.album.client.impl;


import com.atguigu.tingshu.album.client.CategoryFeignClient;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.album.BaseCategory3;
import com.atguigu.tingshu.model.album.BaseCategoryView;
import org.springframework.stereotype.Component;

import java.util.List;

//降级处理类
@Component
public class CategoryDegradeFeignClient implements CategoryFeignClient {
    //被远程调用接口失败后的降级方法
    @Override
    public Result<BaseCategoryView> getCategoryView(Long category3Id) {
        return Result.fail();
    }

    @Override
    public Result<List<BaseCategory3>> findTopBaseCategory3(Long category1Id) {
        return Result.fail();
    }
}
