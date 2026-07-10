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
}
