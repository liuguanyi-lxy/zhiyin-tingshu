package com.atguigu.tingshu.user.service;

import com.atguigu.tingshu.vo.user.UserCollectVo;
import com.baomidou.mybatisplus.core.metadata.IPage;

/**
 * 用户收藏业务接口（MongoDB，集合 userCollect_{userId%100}，收藏对象为声音）。
 */
public interface UserCollectService {

    /**
     * 收藏/取消收藏声音（切换语义）：已收藏则取消、未收藏则收藏。
     * @return 切换后的收藏状态，true=已收藏
     */
    Boolean collectTrack(Long userId, Long trackId);

    /**
     * 查询用户是否已收藏指定声音
     */
    Boolean isCollectTrack(Long userId, Long trackId);

    /**
     * 分页查询"我的收藏"（含声音标题/封面等信息）
     */
    IPage<UserCollectVo> findUserCollectPage(Long page, Long limit, Long userId);
}
