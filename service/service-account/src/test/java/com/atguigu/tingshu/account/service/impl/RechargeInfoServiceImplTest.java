package com.atguigu.tingshu.account.service.impl;

import com.atguigu.tingshu.account.mapper.UserAccountMapper;
import com.atguigu.tingshu.account.mapper.RechargeInfoMapper;
import com.atguigu.tingshu.model.account.RechargeInfo;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 充值入账单元测试（纯 Mockito）。
 * 覆盖【修复#5】：状态条件更新幂等 + 余额原子累加，重复消息不得重复加钱。
 */
@ExtendWith(MockitoExtension.class)
class RechargeInfoServiceImplTest {

    @Mock
    private RechargeInfoMapper rechargeInfoMapper;
    @Mock
    private UserAccountMapper userAccountMapper;
    @InjectMocks
    private RechargeInfoServiceImpl service;

    @BeforeAll
    static void initTableMeta() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), RechargeInfo.class);
    }

    @BeforeEach
    void injectBaseMapper() throws Exception {
        Field baseMapper = com.baomidou.mybatisplus.extension.service.impl.ServiceImpl.class.getDeclaredField("baseMapper");
        baseMapper.setAccessible(true);
        baseMapper.set(service, rechargeInfoMapper);
    }

    private RechargeInfo unpaidRecharge() {
        RechargeInfo rechargeInfo = new RechargeInfo();
        rechargeInfo.setOrderNo("RECHARGE123");
        rechargeInfo.setRechargeStatus("0901");
        rechargeInfo.setRechargeAmount(new BigDecimal("100.00"));
        rechargeInfo.setUserId(8L);
        return rechargeInfo;
    }

    @Test
    void firstCallback_updatesStatusAndIncrementsBalanceAtomically() {
        when(rechargeInfoMapper.selectOne(any())).thenReturn(unpaidRecharge());
        when(rechargeInfoMapper.update(isNull(), any())).thenReturn(1);
        when(userAccountMapper.incrTotalAndAvailable(new BigDecimal("100.00"), 8L)).thenReturn(1);

        service.rechargePaySuccess("RECHARGE123");

        verify(userAccountMapper).incrTotalAndAvailable(new BigDecimal("100.00"), 8L);
    }

    @Test
    void duplicateMessage_neverIncrementsBalanceTwice() {
        when(rechargeInfoMapper.selectOne(any())).thenReturn(unpaidRecharge());
        // 条件更新受影响行数 = 0：充值记录已是"已支付"，重复 MQ 消息
        when(rechargeInfoMapper.update(isNull(), any())).thenReturn(0);

        service.rechargePaySuccess("RECHARGE123");

        verify(userAccountMapper, never()).incrTotalAndAvailable(any(), eq(8L));
    }

    @Test
    void rechargeRecordMissing_throws() {
        when(rechargeInfoMapper.selectOne(any())).thenReturn(null);

        assertThrows(Exception.class, () -> service.rechargePaySuccess("RECHARGE123"));

        verify(userAccountMapper, never()).incrTotalAndAvailable(any(), eq(8L));
    }

    @Test
    void accountMissing_throwsAfterStatusUpdate() {
        when(rechargeInfoMapper.selectOne(any())).thenReturn(unpaidRecharge());
        when(rechargeInfoMapper.update(isNull(), any())).thenReturn(1);
        when(userAccountMapper.incrTotalAndAvailable(new BigDecimal("100.00"), 8L)).thenReturn(0);

        assertThrows(Exception.class, () -> service.rechargePaySuccess("RECHARGE123"));
    }
}
