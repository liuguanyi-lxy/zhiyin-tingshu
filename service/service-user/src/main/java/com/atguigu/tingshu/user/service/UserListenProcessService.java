package com.atguigu.tingshu.user.service;

import com.atguigu.tingshu.model.user.UserListenProcess;
import com.atguigu.tingshu.vo.user.UserListenProcessListVo;
import com.atguigu.tingshu.vo.user.UserListenProcessVo;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.math.BigDecimal;

public interface UserListenProcessService {
    //根据登录校验得到的当前用户ID和前端传入的关于指定专辑的声音和播放进度更新MongoDB中的数据
    void saveOrUpdateListenProcess(Long userId, UserListenProcessVo userListenProcessVo);
    //根据当前用户ID和指定声音ID查询MongoDB中的数据，获取声音的播放进度
    BigDecimal getTrackBreakSecond(Long userId, Long trackId);
    //分页查询"我的收听历史"（含声音/专辑标题、时长、播放进度百分比等信息）
    IPage<UserListenProcessListVo> findUserPage(Long page, Long limit, Long userId);
    //删除指定收听历史（软删除 isShow=0，保留断点数据）
    void deleteListenProcess(Long userId, String id);
    //获取最近一次收听记录，供播放页"上次听到"续播使用（无记录时返回空对象）
    UserListenProcess getLatelyTrack(Long userId);
}
