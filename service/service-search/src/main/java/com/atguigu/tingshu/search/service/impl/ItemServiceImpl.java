package com.atguigu.tingshu.search.service.impl;

import com.atguigu.tingshu.album.client.AlbumInfoFeignClient;
import com.atguigu.tingshu.album.client.CategoryFeignClient;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.model.album.BaseCategoryView;
import com.atguigu.tingshu.search.service.ItemService;
import com.atguigu.tingshu.user.client.UserInfoFeignClient;
import com.atguigu.tingshu.vo.album.AlbumStatVo;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.atguigu.tingshu.common.result.Result;
import org.springframework.util.Assert;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@SuppressWarnings({"all"})
public class ItemServiceImpl implements ItemService {
    @Autowired
    private AlbumInfoFeignClient albumInfoFeignClient;
    @Autowired
    private CategoryFeignClient categoryFeignClient;
    @Autowired
    private UserInfoFeignClient userInfoFeignClient;
    @Override
    public Map<String, Object> getItem(Long albumId) {
        //创建一个map集合存储专辑详情数据
        HashMap<String, Object> AlbumInfoMap = new HashMap<>();
        //使用CompletableFuture实现异步调用多线程任务，实现多任务并发
        //1.根据专辑ID获取专辑基本信息
        CompletableFuture<AlbumInfo> future1 = CompletableFuture.supplyAsync(() ->
                {
                    Result<AlbumInfo> albumInfo = albumInfoFeignClient.getAlbumInfo(albumId);
                    AlbumInfo albumInfoData = albumInfo.getData();
                    Assert.notNull(albumInfoData, "专辑信息不存在");
                    //将专辑基本信息存储到map集合中
                    AlbumInfoMap.put("albumInfo", albumInfoData);
                    return albumInfoData;
                }
        );
        //2.根据第一步查询的专辑基本信息中的三级分类ID获取对应的分类信息
        CompletableFuture<Void> future2 = future1.thenAcceptAsync(albumInfo -> {
            Result<BaseCategoryView> result = categoryFeignClient.getCategoryView(albumInfo.getCategory3Id());
            BaseCategoryView categoryData = result.getData();
            Assert.notNull(categoryData, "分类信息不存在");
            //将分类信息存储到map集合中
            AlbumInfoMap.put("baseCategoryView", categoryData);
        });
        //3.根据第一步查询的专辑基本信息中的用户ID获取用户基本信息
        CompletableFuture<Void> future3 = future1.thenAcceptAsync(albumInfo -> {
            Result<UserInfoVo> result = userInfoFeignClient.getUserInfoVo(albumInfo.getUserId());
            UserInfoVo userInfoVoData = result.getData();
            Assert.notNull(userInfoVoData, "用户信息不存在");
            //将用户信息存储到map集合中
            AlbumInfoMap.put("announcer", userInfoVoData);
        });
        //4.根据专辑ID查询专辑四个统计数据
        CompletableFuture<Void> future4 = CompletableFuture.runAsync(() -> {
            Result<AlbumStatVo> result = albumInfoFeignClient.getAlbumInfoStat(albumId);
            AlbumStatVo albumStatVo = result.getData();
            Assert.notNull(albumStatVo, "专辑统计数据不存在");
            //将专辑统计数据存储到map集合中
            AlbumInfoMap.put("albumStatVo", albumStatVo);
        });
        //等待所有任务执行完毕
        CompletableFuture.allOf(future1, future2,future3,future4).join();
        return AlbumInfoMap;
    }
}
