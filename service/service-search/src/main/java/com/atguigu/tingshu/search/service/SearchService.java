package com.atguigu.tingshu.search.service;

import com.atguigu.tingshu.query.search.AlbumIndexQuery;
import com.atguigu.tingshu.vo.search.AlbumInfoIndexVo;
import com.atguigu.tingshu.vo.search.AlbumSearchResponseVo;

import java.util.List;
import java.util.Map;

public interface SearchService {
    //  上架专辑到es中
    void upperAlbum(Long albumId);
    //  下架专辑
    void lowerAlbum(Long albumId);
    //搜索框进行专辑检索
    AlbumSearchResponseVo search(AlbumIndexQuery albumIndexQuery);
    /**
     * 根据一级分类Id 获取置顶数据
     * @param category1Id
     * @return
     */
    List<Map<String, Object>> channel(Long category1Id);
    //根据输入的关键字返回自动补全的字符串列表
    List<String> completeSuggest(String keyword);
    //更新排行榜
    void updateLatelyAlbumRanking();
    //根据一级分类ID和排序字段获取排行榜列表
    List<AlbumInfoIndexVo> findRankingList(Long category1Id, String dimension);
}
