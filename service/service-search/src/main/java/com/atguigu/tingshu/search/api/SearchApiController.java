package com.atguigu.tingshu.search.api;

import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.query.search.AlbumIndexQuery;
import com.atguigu.tingshu.search.service.SearchService;
import com.atguigu.tingshu.vo.search.AlbumInfoIndexVo;
import com.atguigu.tingshu.vo.search.AlbumSearchResponseVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Tag(name = "搜索专辑管理")
@RestController
@RequestMapping("api/search/albumInfo")
@SuppressWarnings({"all"})
public class SearchApiController {

    @Autowired
    private SearchService searchService;
    //根据专辑id实现上架
    @Operation(summary = "上架专辑到es中,用于专辑搜索")
    @GetMapping("/upperAlbum/{albumId}")
    public Result upperAlbum(@PathVariable Long albumId){
        //  调用服务层方法.
        this.searchService.upperAlbum(albumId);
        //  默认返回
        return Result.ok();
    }
    /**
     * 下架专辑
     * @param albumId
     * @return
     */
    @Operation(summary = "下架专辑,把es中对应专辑信息删除")
    @GetMapping("lowerAlbum/{albumId}")
    public Result lowerAlbum(@PathVariable Long albumId) {
        searchService.lowerAlbum(albumId);
        return Result.ok();
    }
    /**
     * 批量上架
     * @return
     */
    @Operation(summary = "批量上架专辑数据到es中，用于搜索时有数据；【压测配套】上限参数化，可灌 10 万级数据")
    @GetMapping("batchUpperAlbum/{upperId}")
    public Result batchUpperAlbum(@PathVariable long upperId) throws InterruptedException {
        //【压测配套】原串行循环有两个问题：① 专辑 id 存在空洞（删改遗留），
        // upperAlbum 的 Assert.notNull 会抛异常导致整体中断；② 10 万级串行耗时约 2 小时。
        // 现改为：并行加载（12 线程）+ 单条失败跳过计数，返回 ok/skip 统计。
        java.util.concurrent.atomic.AtomicInteger ok = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger skip = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(12);
        try {
            for (long i = 1; i <= upperId; i++) {
                final long albumId = i;
                pool.submit(() -> {
                    try {
                        searchService.upperAlbum(albumId);
                        ok.incrementAndGet();
                    } catch (Exception e) {
                        // 专辑不存在（id 空洞）或其他单条失败：跳过不中断
                        skip.incrementAndGet();
                    }
                });
            }
        } finally {
            pool.shutdown();
            pool.awaitTermination(6, java.util.concurrent.TimeUnit.HOURS);
        }
        return Result.ok("加载完成：ok=" + ok.get() + ", skip=" + skip.get());
    }
    //专辑检索的接口
    @Operation(summary = "搜索框中进行专辑搜索")
    @PostMapping
    public Result search(@RequestBody AlbumIndexQuery albumIndexQuery) throws IOException {
        //调用service
        AlbumSearchResponseVo albumSearchResponseVo = searchService.search(albumIndexQuery);
        return Result.ok(albumSearchResponseVo);
    }
    /**
     * 根据一级分类Id获取数据
     * @param category1Id
     * @return
     */
    @Operation(summary = "获取首页一级分类下频道页数据")
    @GetMapping("channel/{category1Id}")
    public Result channel(@PathVariable Long category1Id) {

        //  调用服务层方法
        List<Map<String, Object>> mapList = null;
        try {
            mapList = searchService.channel(category1Id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return Result.ok(mapList);
    }

    /**
     * 自动补全功能
     * @param keyword
     * @return
     */
    @Operation(summary = "搜索框关键字自动补全")
    @GetMapping("completeSuggest/{keyword}")
    public Result completeSuggest(@PathVariable String keyword) {
        //返回一个字符串列表，每个字符串是带有关键字的一个字符串
        List<String> list = searchService.completeSuggest(keyword);
        return Result.ok(list);
    }
    /**
     * 更新排行榜
     * @return
     */
    @SneakyThrows
    @Operation(summary = "更新所有一级分类下按各种字段排序的排行榜")
    @GetMapping("updateLatelyAlbumRanking")
    public Result updateLatelyAlbumRanking() {
        //  调用服务层方法
        searchService.updateLatelyAlbumRanking();
        //  默认返回
        return Result.ok();
    }
    /**
     * 获取排行榜列表
     * @param category1Id
     * @param dimension
     * @return
     */
    @Operation(summary = "根据一级分类ID和排序字段获取排行榜列表")
    @GetMapping("findRankingList/{category1Id}/{dimension}")
    public Result<List<AlbumInfoIndexVo>> findRankingList(@PathVariable Long category1Id,
                    @PathVariable String dimension) {
        //  调用服务层方法
        List<AlbumInfoIndexVo> infoIndexVoList = searchService.findRankingList(category1Id, dimension);
        //  返回结果集
        return Result.ok(infoIndexVoList);
    }
}

