package com.atguigu.tingshu.order.mapper;

import com.atguigu.tingshu.model.order.OrderInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderInfoMapper extends BaseMapper<OrderInfo> {
    //根据用户id和分页条件查询订单信息,设置了分类条件类Page，mybatisplus会自动帮我们对sql语句进行分页查询
    IPage<OrderInfo> selectUserPage(Page<OrderInfo> pageParam, Long userId);
}
