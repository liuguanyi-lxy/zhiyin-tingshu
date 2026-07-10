package com.atguigu.tingshu.user.service;

import com.atguigu.tingshu.vo.user.UserListenProcessVo;

import java.math.BigDecimal;

public interface UserListenProcessService {
    //根据登录校验得到的当前用户ID和前端传入的关于指定专辑的声音和播放进度更新MongoDB中的数据
    void saveOrUpdateListenProcess(Long userId, UserListenProcessVo userListenProcessVo);
    //根据当前用户ID和指定声音ID查询MongoDB中的数据，获取声音的播放进度
    BigDecimal getTrackBreakSecond(Long userId, Long trackId);
}
