package com.atguigu.tingshu.album.service;

import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.query.album.AlbumInfoQuery;
import com.atguigu.tingshu.vo.album.AlbumInfoVo;
import com.atguigu.tingshu.vo.album.AlbumListVo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import org.apache.ibatis.annotations.Param;

import java.util.List;

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
}
