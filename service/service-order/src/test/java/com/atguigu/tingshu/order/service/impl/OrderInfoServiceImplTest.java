package com.atguigu.tingshu.order.service.impl;

import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.model.order.OrderDetail;
import com.atguigu.tingshu.model.order.OrderInfo;
import com.atguigu.tingshu.order.mapper.OrderDetailMapper;
import com.atguigu.tingshu.order.mapper.OrderInfoMapper;
import com.atguigu.tingshu.user.client.UserInfoFeignClient;
import com.atguigu.tingshu.vo.user.UserPaidRecordVo;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订单支付成功处理单元测试（纯 Mockito）。
 * 覆盖【修复#5】：条件更新幂等 —— 重复 MQ 消息不得重复发放用户购买权益。
 */
@ExtendWith(MockitoExtension.class)
class OrderInfoServiceImplTest {

    @Mock
    private OrderInfoMapper orderInfoMapper;
    @Mock
    private OrderDetailMapper orderDetailMapper;
    @Mock
    private UserInfoFeignClient userInfoFeignClient;
    @InjectMocks
    private OrderInfoServiceImpl service;

    @BeforeAll
    static void initTableMeta() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), OrderInfo.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), OrderDetail.class);
    }

    private OrderInfo unpaidAlbumOrder() {
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setId(1L);
        orderInfo.setOrderNo("ORDER123");
        orderInfo.setOrderStatus(SystemConstant.ORDER_STATUS_UNPAID);
        orderInfo.setUserId(8L);
        orderInfo.setItemType(SystemConstant.ORDER_ITEM_TYPE_ALBUM);
        orderInfo.setPayWay(SystemConstant.ORDER_PAY_WAY_WEIXIN);
        OrderDetail detail = new OrderDetail();
        detail.setItemId(66L);
        orderInfo.setOrderDetailList(List.of(detail));
        return orderInfo;
    }

    @Test
    void firstPaySuccess_grantsPaidRecordExactlyOnce() {
        when(orderInfoMapper.selectOne(any())).thenReturn(unpaidAlbumOrder());
        when(orderDetailMapper.selectList(any())).thenReturn(List.of(unpaidAlbumOrder().getOrderDetailList().get(0)));
        when(orderInfoMapper.update(isNull(), any())).thenReturn(1);

        service.updateOrderSuccess("ORDER123");

        ArgumentCaptor<UserPaidRecordVo> captor = ArgumentCaptor.forClass(UserPaidRecordVo.class);
        verify(userInfoFeignClient, times(1)).savePaidRecord(captor.capture());
        UserPaidRecordVo vo = captor.getValue();
        assertEquals("ORDER123", vo.getOrderNo());
        assertEquals(8L, vo.getUserId());
        assertEquals(List.of(66L), vo.getItemIdList());
    }

    @Test
    void duplicateMessage_neverGrantsPaidRecordTwice() {
        when(orderInfoMapper.selectOne(any())).thenReturn(unpaidAlbumOrder());
        when(orderDetailMapper.selectList(any())).thenReturn(List.of(unpaidAlbumOrder().getOrderDetailList().get(0)));
        // 条件更新受影响行数 = 0：订单已是"已支付"，重复 MQ 消息
        when(orderInfoMapper.update(isNull(), any())).thenReturn(0);

        service.updateOrderSuccess("ORDER123");

        verify(userInfoFeignClient, never()).savePaidRecord(any());
    }

    @Test
    void orderMissing_throwsWithoutGrant() {
        when(orderInfoMapper.selectOne(any())).thenReturn(null);

        assertThrows(Exception.class, () -> service.updateOrderSuccess("ORDER123"));

        verify(userInfoFeignClient, never()).savePaidRecord(any());
        verify(orderInfoMapper, never()).update(isNull(), any());
    }
}
