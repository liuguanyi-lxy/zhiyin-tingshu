package com.atguigu.tingshu.account.mapper;

import com.atguigu.tingshu.model.account.UserAccount;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

@Mapper
public interface UserAccountMapper extends BaseMapper<UserAccount> {
    //检查账户余额是否充足，充足就扣减余额
    int checkAndDeduct(@Param("amount") BigDecimal amount, @Param("userId") Long userId);

    UserAccount selectForDeduction(@Param("userId") Long userId);

    //【修复#5】充值入账：数据库原子累加余额，替代"先查后改"的丢失更新写法
    int incrTotalAndAvailable(BigDecimal amount, Long userId);
}
