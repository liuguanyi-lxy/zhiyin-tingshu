package com.atguigu.tingshu.search.api;

import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.query.search.AlbumIndexQuery;
import com.atguigu.tingshu.search.service.SearchService;
import com.atguigu.tingshu.vo.search.AlbumSearchResponseVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
    @Operation(summary = "批量上架专辑数据到es中，用于搜索时有数据")
    @GetMapping("batchUpperAlbum")
    public Result batchUpperAlbum(){
        //  循环
        for (long i = 1; i <= 1500; i++) {
            searchService.upperAlbum(i);
        }
        //  返回数据
        return Result.ok();
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
    @Operation(summary = "获取频道页数据")
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
}

