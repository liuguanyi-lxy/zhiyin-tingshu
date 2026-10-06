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

/**
 * 在所有测试方法执行之前初始化表元数据的静态方法
 * 使用@BeforeAll注解标记，这是JUnit 5中的生命周期方法，会在所有测试方法执行前运行一次
 * 通常用于执行一次性设置操作，如初始化数据库连接、创建表结构等
 */
    @BeforeAll
    static void initTableMeta() {
    // 使用TableInfoHelper初始化RechargeInfo类的表信息
    // 参数包括：
    // 1. MapperBuilderAssistant：MyBatis映射器构建助手，用于构建映射器
    // 2. MybatisConfiguration：MyBatis配置对象，包含MyBatis的核心配置
    // 3. RechargeInfo.class：要初始化表信息的实体类
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), RechargeInfo.class);
    }

/**
 * 在每个测试方法执行前执行，用于注入baseMapper
 * 通过反射机制将rechargeInfoMapper注入到ServiceImpl的baseMapper字段中
 *
 * @throws Exception 可能抛出的异常，包括NoSuchFieldException和IllegalAccessException
 */
    @BeforeEach
    void injectBaseMapper() throws Exception {
    // 获取ServiceImpl类中声明的baseMapper字段
        Field baseMapper = com.baomidou.mybatisplus.extension.service.impl.ServiceImpl.class.getDeclaredField("baseMapper");
    // 设置该字段为可访问，即使它是私有的
        baseMapper.setAccessible(true);
    // 将rechargeInfoMapper实例设置到service对象的baseMapper字段中
        baseMapper.set(service, rechargeInfoMapper);
    }

/**
 * 获取未支付充值信息的方法
 * @return 返回一个包含未支付充值信息的RechargeInfo对象
 */
    private RechargeInfo unpaidRecharge() {
    // 创建一个新的RechargeInfo对象
        RechargeInfo rechargeInfo = new RechargeInfo();
    // 设置订单号为"RECHARGE123"
        rechargeInfo.setOrderNo("RECHARGE123");
    // 设置充值状态为"0901"，可能表示未支付状态
        rechargeInfo.setRechargeStatus("0901");
    // 设置充值金额为100.00元
        rechargeInfo.setRechargeAmount(new BigDecimal("100.00"));
    // 设置用户ID为8
        rechargeInfo.setUserId(8L);
    // 返回填充好的充值信息对象
        return rechargeInfo;
    }

/**
 * 测试方法：验证首次回调时状态更新和余额增加的原子性操作
 * 该测试用例模拟了充值成功后的回调处理流程，验证系统是否正确更新充值状态并增加用户余额
 */
    @Test
    void firstCallback_updatesStatusAndIncrementsBalanceAtomically() {
    // 模拟充值信息查询：当查询任何充值信息时，返回未支付的充值记录
        when(rechargeInfoMapper.selectOne(any())).thenReturn(unpaidRecharge());
    // 模拟充值信息更新：当更新条件为null时，执行任意更新操作并返回1表示更新成功
        when(rechargeInfoMapper.update(isNull(), any())).thenReturn(1);
    // 模拟用户账户余额增加：当增加100.00元余额给用户ID为8的用户时，返回1表示操作成功
        when(userAccountMapper.incrTotalAndAvailable(new BigDecimal("100.00"), 8L)).thenReturn(1);

    // 执行充值成功回调方法：处理订单号RECHARGE123的充值成功回调
        service.rechargePaySuccess("RECHARGE123");

    // 验证用户账户余额增加方法是否被正确调用：验证是否增加了100.00元余额给用户ID为8的用户
        verify(userAccountMapper).incrTotalAndAvailable(new BigDecimal("100.00"), 8L);
    }

    @Test
    void duplicateMessage_neverIncrementsBalanceTwice() {
        // 设置 rechargeInfoMapper.selectOne 方法的返回值为未支付的充值记录
        when(rechargeInfoMapper.selectOne(any())).thenReturn(unpaidRecharge());
        // 条件更新受影响行数 = 0：充值记录已是"已支付"，重复 MQ 消息
        when(rechargeInfoMapper.update(isNull(), any())).thenReturn(0);

        service.rechargePaySuccess("RECHARGE123");

        verify(userAccountMapper, never()).incrTotalAndAvailable(any(), eq(8L));
    }

/**
 * 测试当充值记录不存在时的情况，预期会抛出异常
 * 这个测试用例验证了当充值记录查询结果为null时的异常处理逻辑
 */
    @Test
    void rechargeRecordMissing_throws() {
    // 模拟充值信息查询结果为null，即不存在该充值记录
        when(rechargeInfoMapper.selectOne(any())).thenReturn(null);

    // 验证调用rechargePaySuccess方法时会抛出异常
        assertThrows(Exception.class, () -> service.rechargePaySuccess("RECHARGE123"));

    // 验证用户账户总额和可用余额不会被增加
        verify(userAccountMapper, never()).incrTotalAndAvailable(any(), eq(8L));
    }

/**
 * 测试用例：充值成功后账户缺失异常情况
 * 验证在更新充值状态后，如果账户信息缺失是否抛出异常
 */
    @Test
    void accountMissing_throwsAfterStatusUpdate() {
        // 模拟充值信息查询，返回未支付的充值记录
        when(rechargeInfoMapper.selectOne(any())).thenReturn(unpaidRecharge());
        // 模拟充值信息更新，参数为空值，返回更新成功
        when(rechargeInfoMapper.update(isNull(), any())).thenReturn(1);
        // 模拟用户账户增加操作，增加100.00元到账户余额，用户ID为8L，返回操作失败
        when(userAccountMapper.incrTotalAndAvailable(new BigDecimal("100.00"), 8L)).thenReturn(0);

        // 验证执行充值成功处理时抛出异常
        assertThrows(Exception.class, () -> service.rechargePaySuccess("RECHARGE123"));
    }
}
