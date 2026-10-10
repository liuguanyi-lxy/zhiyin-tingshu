package com.atguigu.tingshu.album.service;

import com.atguigu.tingshu.model.album.AlbumAttributeValue;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.query.album.AlbumInfoQuery;
import com.atguigu.tingshu.vo.album.AlbumInfoVo;
import com.atguigu.tingshu.vo.album.AlbumListVo;
import com.atguigu.tingshu.vo.album.AlbumStatVo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface AlbumInfoService extends IService<AlbumInfo> {

    //保存专辑
    void saveAlbumInfo(AlbumInfoVo albumInfoVo);
    //分页查询专辑列表
    IPage<AlbumListVo> selectAlbumListPage(@Param("pageParam") Page<AlbumListVo> pageParam,@Param("albumInfoQuery") AlbumInfoQuery albumInfoQuery);
    //根据id删除专辑信息
    void removeAlbumInfo(String albumId);
    //根据专辑id获取专辑信息
    AlbumInfo getAlbumInfo(Long albumId);
    //根据专辑ID修改专辑信息
    void updateAlbumInfo(Long albumId, AlbumInfoVo albumInfoVo);
    //根据用户id查询用户专辑列表
    List<AlbumInfo> findUserAlbumList(Long userId);
    //根据专辑id获取四个统计数据
    AlbumStatVo getAlbumInfoStat(Long albumId);
    //根据专辑Id 获取到专辑标签列表
    List<AlbumAttributeValue> findAlbumAttributeValueByAlbumId(Long albumId);
    //根据专辑ID和统计类型修改专辑的播放量
    void updateStat(Long albumId, String statType,Integer count);

    //【压测基线】LIKE 模糊查询，用于与 ES 检索做性能对照（见 docs/压测指引.md）
    java.util.List<java.util.Map<String, Object>> likeBenchmark(String keyword);

    //根据专辑ID列表批量查询专辑信息（供订阅列表等场景组装数据，避免逐条 Feign 的 N+1）
    List<AlbumInfo> findAlbumInfoList(List<Long> albumIds);
}