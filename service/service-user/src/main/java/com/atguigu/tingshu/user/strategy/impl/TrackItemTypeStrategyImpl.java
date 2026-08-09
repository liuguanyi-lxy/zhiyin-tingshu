package com.atguigu.tingshu.user.strategy.impl;

import com.atguigu.tingshu.album.client.TrackInfoFeignClient;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.model.user.UserPaidTrack;
import com.atguigu.tingshu.user.mapper.UserPaidTrackMapper;
import com.atguigu.tingshu.user.strategy.ItemTypeStrategy;
import com.atguigu.tingshu.vo.user.UserPaidRecordVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

//购买声音实现类,定义Bean对象的ID为声音类型
//将声音类型购买策略实现类beanId跟前端提交购买项目类型一致
@Component(SystemConstant.ORDER_ITEM_TYPE_TRACK)
public class TrackItemTypeStrategyImpl implements ItemTypeStrategy {

    @Autowired
    private UserPaidTrackMapper userPaidTrackMapper;

    @Autowired
    private TrackInfoFeignClient trackInfoFeignClient;

    //添加声音购买记录
    @Override
    public void savePaidRecord(UserPaidRecordVo userPaidRecordVo) {
        //  声音 user_paid_track
        LambdaQueryWrapper<UserPaidTrack> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserPaidTrack::getUserId, userPaidRecordVo.getUserId());
        wrapper.eq(UserPaidTrack::getOrderNo, userPaidRecordVo.getOrderNo());
        Long count = userPaidTrackMapper.selectCount(wrapper);
        if (count > 0) {
            return;
        }
        //  获取专辑Id
        Result<TrackInfo> trackInfoResult = trackInfoFeignClient.getTrackInfo(userPaidRecordVo.getItemIdList().get(0));
        TrackInfo trackInfo = trackInfoResult.getData();
        //  声音Id 有可能一次购买多个.需要循环遍历
        userPaidRecordVo.getItemIdList().stream().forEach(trackId -> {
            //  保存数据：
            UserPaidTrack userPaidTrack = new UserPaidTrack();
            userPaidTrack.setTrackId(trackId);
            userPaidTrack.setOrderNo(userPaidRecordVo.getOrderNo());
            userPaidTrack.setUserId(userPaidRecordVo.getUserId());
            //  获取专辑Id
            userPaidTrack.setAlbumId(trackInfo.getAlbumId());
            userPaidTrackMapper.insert(userPaidTrack);
        });
    }
}
