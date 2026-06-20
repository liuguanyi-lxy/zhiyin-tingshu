package com.atguigu.tingshu.album.mapper;

import com.atguigu.tingshu.model.album.BaseAttribute;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BaseAttributeMapper extends BaseMapper<BaseAttribute> {
    //根据一级分类ID查询对应的标签数据
    List<BaseAttribute> selectAttribute(@Param("category1Id") Long category1Id);
}
