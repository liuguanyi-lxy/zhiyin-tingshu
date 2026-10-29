package com.atguigu.tingshu.album.mapper;

import com.atguigu.tingshu.model.album.TrackStat;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TrackStatMapper extends BaseMapper<TrackStat> {

    //【修复#3】原子累加统计数（同 AlbumStatMapper.incrAlbumStatNum）
    int incrTrackStatNum(@Param("trackId") Long trackId, @Param("statType") String statType, @Param("count") Integer count);
}
