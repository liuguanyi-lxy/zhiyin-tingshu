package com.atguigu.tingshu.album.mapper;

import com.atguigu.tingshu.model.album.AlbumStat;
import com.atguigu.tingshu.vo.album.AlbumStatVo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AlbumStatMapper extends BaseMapper<AlbumStat> {
    //根据专辑ID获取专辑的四个统计数据
    AlbumStatVo getAlbumInfoStat(Long albumId);

    //【修复#3】原子累加统计数：SET stat_num = stat_num + n，由数据库行锁保证并发安全，
    // 替代"先查后改"的丢失更新写法；返回受影响行数，0 表示统计记录不存在
    int incrAlbumStatNum(@Param("albumId") Long albumId, @Param("statType") String statType, @Param("count") Integer count);
}
