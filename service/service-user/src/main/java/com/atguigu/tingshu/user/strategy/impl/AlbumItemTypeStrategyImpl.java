package com.atguigu.tingshu.user.strategy.impl;

import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.model.user.UserPaidAlbum;
import com.atguigu.tingshu.user.mapper.UserPaidAlbumMapper;
import com.atguigu.tingshu.user.strategy.ItemTypeStrategy;
import com.atguigu.tingshu.vo.user.UserPaidRecordVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

//购买专辑实现类,定义Bean对象的ID为专辑类型
//将专辑类型购买策略实现类beanId跟前端提交购买项目类型一致
@Component(SystemConstant.ORDER_ITEM_TYPE_ALBUM)
public class AlbumItemTypeStrategyImpl implements ItemTypeStrategy {

    @Autowired
    private UserPaidAlbumMapper userPaidAlbumMapper;

    //添加专辑购买记录
    @Override
    public void savePaidRecord(UserPaidRecordVo userPaidRecordVo) {
        //  专辑 user_paid_album
        //  防止重复添加.
        LambdaQueryWrapper<UserPaidAlbum> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserPaidAlbum::getUserId, userPaidRecordVo.getUserId());
        //  wrapper.eq(UserPaidAlbum::getAlbumId,userPaidRecordVo.getItemIdList().get(0));
        wrapper.eq(UserPaidAlbum::getOrderNo, userPaidRecordVo.getOrderNo());
        UserPaidAlbum userPaidAlbum = userPaidAlbumMapper.selectOne(wrapper);
        //能查询得到就说明用户购买专辑记录已经添加
        if (null != userPaidAlbum) {
            return;
        }
        //  新增：
        userPaidAlbum = new UserPaidAlbum();
        userPaidAlbum.setUserId(userPaidRecordVo.getUserId());
        userPaidAlbum.setOrderNo(userPaidRecordVo.getOrderNo());
        //  专辑Id 声音Id vip_service_config.id ==> order_detial.item_id;
        userPaidAlbum.setAlbumId(userPaidRecordVo.getItemIdList().get(0));
        userPaidAlbumMapper.insert(userPaidAlbum);
    }
}
