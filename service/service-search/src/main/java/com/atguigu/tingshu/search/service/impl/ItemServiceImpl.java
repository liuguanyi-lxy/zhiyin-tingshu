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

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

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
    @Autowired(required = false)
    private ThreadPoolExecutor threadPoolExecutor;

    //根据专辑id查询专辑详情信息
    @Override
    public Map<String, Object> getItem(Long albumId) {
        //创建一个map集合存储专辑详情数据
        //【修复：并发Bug】future1~future4 在不同线程往同一个 map 写入，
        // HashMap 非线程安全会丢数据甚至抛异常，改用 ConcurrentHashMap
        Map<String, Object> albumInfoMap = new ConcurrentHashMap<>();
        //【联调修复】使用受管理的线程池执行 Feign 调用：
        // ThreadPoolExecutorConfig 中 ThreadFactory 统一为池内线程设置了 LaunchedURLClassLoader，
        // 避免默认 ForkJoinPool.commonPool 在 Java 17 下 worker 线程为 AppClassLoader 导致子上下文解析失败。
        Executor executor = threadPoolExecutor != null ? threadPoolExecutor : Runnable::run;

        //使用CompletableFuture实现异步调用多线程任务，实现多任务并发
        //1.根据专辑ID获取专辑基本信息
        CompletableFuture<AlbumInfo> future1 = CompletableFuture.supplyAsync(() ->
                {
                    Result<AlbumInfo> albumInfo = albumInfoFeignClient.getAlbumInfo(albumId);
                    AlbumInfo albumInfoData = (albumInfo != null) ? albumInfo.getData() : null;
                    Assert.notNull(albumInfoData, "专辑信息不存在");
                    //将专辑基本信息存储到map集合中
                    albumInfoMap.put("albumInfo", albumInfoData);
                    return albumInfoData;
                }, executor
        );
        //2.根据第一步查询的专辑基本信息中的三级分类ID获取对应的分类信息
        CompletableFuture<Void> future2 = future1.thenAcceptAsync(albumInfo -> {
            Result<BaseCategoryView> result = categoryFeignClient.getCategoryView(albumInfo.getCategory3Id());
            BaseCategoryView categoryData = (result != null) ? result.getData() : null;
            Assert.notNull(categoryData, "分类信息不存在");
            //将分类信息存储到map集合中
            albumInfoMap.put("baseCategoryView", categoryData);
        }, executor);
        //3.根据第一步查询的专辑基本信息中的用户ID获取用户基本信息
        CompletableFuture<Void> future3 = future1.thenAcceptAsync(albumInfo -> {
            Result<UserInfoVo> result = userInfoFeignClient.getUserInfoVo(albumInfo.getUserId());
            UserInfoVo userInfoVoData = (result != null) ? result.getData() : null;
            //【联调修复】主播资料缺失（用户注销/压测库未同步用户/Feign降级）不应阻断整个详情接口：
            // 专辑可看是主流程，昵称是附属信息，前端 detail 页对 nickname 缺失已有兜底文案。
            if (userInfoVoData == null) {
                log.warn("专辑 {} 的主播信息缺失，userId={}，详情降级返回（不含 announcer）", albumId, albumInfo.getUserId());
                return;
            }
            //将用户信息存储到map集合中
            albumInfoMap.put("announcer", userInfoVoData);
        }, executor);
        //4.根据专辑ID查询专辑四个统计数据
        CompletableFuture<Void> future4 = CompletableFuture.runAsync(() -> {
            Result<AlbumStatVo> result = albumInfoFeignClient.getAlbumInfoStat(albumId);
            AlbumStatVo albumStatVo = (result != null) ? result.getData() : null;
            //【联调修复】统计服务降级时提供空统计，不阻断详情页主流程
            if (albumStatVo == null) {
                log.warn("专辑 {} 统计数据缺失或降级，兜底默认空统计", albumId);
                albumStatVo = new AlbumStatVo();
                albumStatVo.setAlbumId(albumId);
            }
            //将专辑统计数据存储到map集合中
            albumInfoMap.put("albumStatVo", albumStatVo);
        }, executor);
        //等待所有任务执行完毕
        CompletableFuture.allOf(future1, future2, future3, future4).join();
        return albumInfoMap;
    }
}
