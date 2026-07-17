package com.atguigu.tingshu.order.service;

import com.atguigu.tingshu.model.order.OrderInfo;
import com.atguigu.tingshu.vo.order.OrderInfoVo;
import com.atguigu.tingshu.vo.order.TradeVo;
import com.baomidou.mybatisplus.extension.service.IService;

public interface OrderInfoService extends IService<OrderInfo> {

    //根据传入的购买商品信息封装对应的订单信息
    OrderInfoVo trade(TradeVo tradeVo, Long userId);

    //取消订单
    //调用方法，根据orderId取消订单
    void cancelOrder(Long orderId);

    //立即结算后进入支付界面生成订单
    String submitOrder(OrderInfoVo orderInfoVo, Long userId);
}
