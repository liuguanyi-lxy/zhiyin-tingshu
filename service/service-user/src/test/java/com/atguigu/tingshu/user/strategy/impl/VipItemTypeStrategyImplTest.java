package com.atguigu.tingshu.user.strategy.impl;

import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.model.user.UserInfo;
import com.atguigu.tingshu.model.user.UserVipService;
import com.atguigu.tingshu.model.user.VipServiceConfig;
import com.atguigu.tingshu.user.mapper.UserInfoMapper;
import com.atguigu.tingshu.user.mapper.UserVipServiceMapper;
import com.atguigu.tingshu.user.mapper.VipServiceConfigMapper;
import com.atguigu.tingshu.vo.user.UserPaidRecordVo;
import org.joda.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 购买 VIP 策略单元测试：非 VIP 基准时间为当前时间，未过期 VIP 以旧到期时间续期，
 * 并同步更新 user_info 的 isVip 与 vipExpireTime。
 */
@ExtendWith(MockitoExtension.class)
class VipItemTypeStrategyImplTest {

    @Mock
    private UserInfoMapper userInfoMapper;
    @Mock
    private VipServiceConfigMapper vipServiceConfigMapper;
    @Mock
    private UserVipServiceMapper userVipServiceMapper;
    @InjectMocks
    private VipItemTypeStrategyImpl strategy;

    private UserPaidRecordVo recordVo() {
        UserPaidRecordVo vo = new UserPaidRecordVo();
        vo.setOrderNo("ORDER123");
        vo.setUserId(9L);
        vo.setItemType(SystemConstant.ORDER_ITEM_TYPE_VIP);
        vo.setItemIdList(Collections.singletonList(5L));
        return vo;
    }

    private VipServiceConfig config(int months) {
        VipServiceConfig config = new VipServiceConfig();
        config.setServiceMonth(months);
        return config;
    }

    @Test
    void nonVipUser_expireTimeIsNowPlusMonthsAndUserUpdated() {
        UserInfo userInfo = new UserInfo();
        userInfo.setId(9L);
        userInfo.setIsVip(0);
        when(userInfoMapper.selectById(9L)).thenReturn(userInfo);
        when(vipServiceConfigMapper.selectById(5L)).thenReturn(config(3));

        long before = System.currentTimeMillis();
        strategy.savePaidRecord(recordVo());
        long after = System.currentTimeMillis();

        ArgumentCaptor<UserVipService> captor = ArgumentCaptor.forClass(UserVipService.class);
        verify(userVipServiceMapper).insert(captor.capture());
        Date expireTime = captor.getValue().getExpireTime();
        long expectLow = new LocalDateTime(new Date(before)).plusMonths(3).toDate().getTime();
        long expectHigh = new LocalDateTime(new Date(after)).plusMonths(3).toDate().getTime();
        org.junit.jupiter.api.Assertions.assertTrue(
                expireTime.getTime() >= expectLow - 5000 && expireTime.getTime() <= expectHigh + 5000);
        assertEquals("ORDER123", captor.getValue().getOrderNo());

        ArgumentCaptor<UserInfo> userCaptor = ArgumentCaptor.forClass(UserInfo.class);
        verify(userInfoMapper).updateById(userCaptor.capture());
        assertEquals(1, userCaptor.getValue().getIsVip());
        assertEquals(expireTime, userCaptor.getValue().getVipExpireTime());
    }

    @Test
    void unexpiredVipUser_renewsFromOldExpireTime() {
        Date oldExpire = new Date(System.currentTimeMillis() + 10L * 24 * 60 * 60 * 1000);
        UserInfo userInfo = new UserInfo();
        userInfo.setId(9L);
        userInfo.setIsVip(1);
        userInfo.setVipExpireTime(oldExpire);
        when(userInfoMapper.selectById(9L)).thenReturn(userInfo);
        when(vipServiceConfigMapper.selectById(5L)).thenReturn(config(1));

        strategy.savePaidRecord(recordVo());

        ArgumentCaptor<UserVipService> captor = ArgumentCaptor.forClass(UserVipService.class);
        verify(userVipServiceMapper).insert(captor.capture());
        Date expect = new LocalDateTime(oldExpire).plusMonths(1).toDate();
        assertEquals(expect, captor.getValue().getExpireTime());
    }

    @Test
    void expiredVipUser_expireTimeIsNowPlusMonths() {
        // VIP 但已过期：基准时间回到当前时间，而不是过期时间
        Date expired = new Date(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000);
        UserInfo userInfo = new UserInfo();
        userInfo.setId(9L);
        userInfo.setIsVip(1);
        userInfo.setVipExpireTime(expired);
        when(userInfoMapper.selectById(9L)).thenReturn(userInfo);
        when(vipServiceConfigMapper.selectById(5L)).thenReturn(config(6));

        long before = System.currentTimeMillis();
        strategy.savePaidRecord(recordVo());

        ArgumentCaptor<UserVipService> captor = ArgumentCaptor.forClass(UserVipService.class);
        verify(userVipServiceMapper).insert(captor.capture());
        long expectLow = new LocalDateTime(new Date(before)).plusMonths(6).toDate().getTime() - 5000;
        org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().getExpireTime().getTime() >= expectLow);
    }
}
