package com.atguigu.tingshu.user.strategy;

import com.atguigu.tingshu.vo.user.UserPaidRecordVo;
//定义策略接口
public interface ItemTypeStrategy {
    //定义添加购买记录方法
    public void savePaidRecord(UserPaidRecordVo userPaidRecordVo);
}
