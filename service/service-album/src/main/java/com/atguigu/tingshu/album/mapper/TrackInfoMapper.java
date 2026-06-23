package com.atguigu.tingshu.album.mapper;

import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.query.album.TrackInfoQuery;
import com.atguigu.tingshu.vo.album.TrackListVo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.constraints.Positive;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TrackInfoMapper extends BaseMapper<TrackInfo> {
    //因为sql语句比较简单，并且使用LambQuerywarraper不方便实现聚合函数查询所以使用注解方式
    @Select("SELECT max(order_num) from track_info where album_id=#{albumId} GROUP BY album_id")
    Integer selectMaxOrderNUm(@Positive(message = "专辑Id不能为空") Long albumId);
    //根据分页条件和查询条件查询用户的声音列表，mybatis-plus分页拦截器会自动封装分页数据
    IPage<TrackListVo> selectUserTrackPage(@Param("pageParam") Page<TrackListVo> pageParam,@Param("trackInfoQuery") TrackInfoQuery trackInfoQuery);
}
