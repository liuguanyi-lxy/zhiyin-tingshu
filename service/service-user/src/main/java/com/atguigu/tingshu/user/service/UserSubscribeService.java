package com.atguigu.tingshu.user.service;

import com.atguigu.tingshu.vo.user.UserSubscribeVo;
import com.baomidou.mybatisplus.core.metadata.IPage;

/**
 * 用户订阅业务接口（MongoDB，集合 userSubscribe_{userId%100}）。
 */
public interface UserSubscribeService {

    /**
     * 订阅/取消订阅专辑（切换语义）：已订阅则取消、未订阅则订阅。
     * @return 切换后的订阅状态，true=已订阅
     */
    Boolean subscribeAlbum(Long userId, Long albumId);

    /**
     * 查询用户是否已订阅指定专辑
     */
    Boolean isSubscribeAlbum(Long userId, Long albumId);

    /**
     * 分页查询"我的订阅"（含专辑标题/封面等信息）
     */
    IPage<UserSubscribeVo> findUserSubscribePage(Long page, Long limit, Long userId);
}
