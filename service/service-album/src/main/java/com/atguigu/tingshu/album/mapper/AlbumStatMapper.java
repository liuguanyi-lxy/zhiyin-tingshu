package com.atguigu.tingshu.album.mapper;

import com.atguigu.tingshu.model.album.AlbumStat;
import com.atguigu.tingshu.vo.album.AlbumStatVo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface AlbumStatMapper extends BaseMapper<AlbumStat> {
    //根据专辑ID获取专辑的四个统计数据
    AlbumStatVo getAlbumInfoStat(Long albumId);
}
