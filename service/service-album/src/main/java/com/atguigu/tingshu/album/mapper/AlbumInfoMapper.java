package com.atguigu.tingshu.album.mapper;

import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.query.album.AlbumInfoQuery;
import com.atguigu.tingshu.vo.album.AlbumListVo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AlbumInfoMapper extends BaseMapper<AlbumInfo> {
    //根据条件查询专辑列表
    IPage<AlbumListVo> selectAlbumListPage(Page<AlbumListVo> pageParam, AlbumInfoQuery albumInfoQuery);

    //【压测基线】LIKE 模糊查询（不走索引，全表扫描），用于与 ES 检索做性能对照
    List<Map<String, Object>> selectLikeBenchmark(@Param("keyword") String keyword);
}
