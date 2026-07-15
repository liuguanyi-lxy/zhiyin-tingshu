package com.atguigu.tingshu.user.service;

import com.atguigu.tingshu.model.user.UserInfo;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

public interface UserInfoService extends IService<UserInfo> {

    /**
     * 根据userId 获取用户登录信息
     *
     * @param userId
     * @return
     */
    UserInfoVo getUserInfoVoByUserId(Long userId);
    //根据专辑ID和声音ID列表判断声音是否被购买，key是声音ID，value是代表是否被购买
    Map<Long, Integer> userIsPaidTrack(Long userId, Long albumId, List<Long> trackIdList);
    //根据专辑ID和用户ID判断专辑是否被购买
    Boolean isPaidAlbum(Long albumId, Long userId);
    //根据专辑ID和用户ID查询用户购买过哪些声音，返回这些声音ID列表
    List<Long> findPaidUserTrackList(Long userId, Long albumId);
}
