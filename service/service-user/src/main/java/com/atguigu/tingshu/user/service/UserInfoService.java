package com.atguigu.tingshu.user.service;

import com.atguigu.tingshu.model.user.UserInfo;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import com.baomidou.mybatisplus.extension.service.IService;

public interface UserInfoService extends IService<UserInfo> {

    /**
     * 根据userId 获取用户登录信息
     *
     * @param userId
     * @return
     */
    UserInfoVo getUserInfoVoByUserId(Long userId);
}
