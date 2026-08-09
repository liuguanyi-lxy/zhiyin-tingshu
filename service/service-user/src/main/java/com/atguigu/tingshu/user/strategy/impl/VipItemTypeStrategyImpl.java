package com.atguigu.tingshu.user.strategy.impl;

import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.model.user.UserInfo;
import com.atguigu.tingshu.model.user.UserVipService;
import com.atguigu.tingshu.model.user.VipServiceConfig;
import com.atguigu.tingshu.user.mapper.UserInfoMapper;
import com.atguigu.tingshu.user.mapper.UserVipServiceMapper;
import com.atguigu.tingshu.user.mapper.VipServiceConfigMapper;
import com.atguigu.tingshu.user.strategy.ItemTypeStrategy;
import com.atguigu.tingshu.vo.user.UserPaidRecordVo;
import org.joda.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

//购买vip的策略实现类，设置Bean对象的ID为vip类型
//将VIP类型购买策略实现类beanId跟前端提交购买项目类型一致
@Component(SystemConstant.ORDER_ITEM_TYPE_VIP)
public class VipItemTypeStrategyImpl implements ItemTypeStrategy {

    @Autowired
    private UserInfoMapper userInfoMapper;


    @Autowired
    private VipServiceConfigMapper vipServiceConfigMapper;


    @Autowired
    private UserVipServiceMapper userVipServiceMapper;

    //添加vip购买记录
    @Override
    public void savePaidRecord(UserPaidRecordVo userPaidRecordVo) {
        //  vip user_vip_service 保存： vip:续期
        //  获取用户对象信息.
        UserInfo userInfo = userInfoMapper.selectById(userPaidRecordVo.getUserId());
        //计算新的vip过期时间的基准时间
        Date BaseTime = new Date();
        //  判断是否要做续期：用户为vip并且用户的vip过期时间在现在时间之后
        if (userInfo.getIsVip().intValue()==1 && userInfo.getVipExpireTime().after(new Date())){
            //基准时间设置为用户购买VIP前的过期时间
            BaseTime = userInfo.getVipExpireTime();
        }
        //获取购买的vip套餐的配置信息
        VipServiceConfig vipServiceConfig = vipServiceConfigMapper.selectById(userPaidRecordVo.getItemIdList().get(0));
        //购买的vip时长
        Integer serviceMonth = vipServiceConfig.getServiceMonth();
        //计算购买VIP后的过期时间
        Date expireTime = new LocalDateTime(BaseTime).plusMonths(serviceMonth).toDate();
        //创建用户购买VIP服务对象
        UserVipService userVipService = new UserVipService();
        userVipService.setOrderNo(userPaidRecordVo.getOrderNo());
        userVipService.setUserId(userPaidRecordVo.getUserId());
        userVipService.setStartTime(new Date());
        userVipService.setExpireTime(expireTime);
        //  保存
        userVipServiceMapper.insert(userVipService);
        //  修改user_info.is_vip=1;
        userInfo.setIsVip(1);
        //  设置过期时间
        userInfo.setVipExpireTime(expireTime);
        //  手动模拟异常信息。
        //  int i = 1/0;
        //  修改用户关于VIP部分的信息
        this.userInfoMapper.updateById(userInfo);
    }
}
