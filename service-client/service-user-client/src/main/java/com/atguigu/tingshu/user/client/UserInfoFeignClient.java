package com.atguigu.tingshu.user.client;

import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.user.client.impl.UserInfoDegradeFeignClient;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import com.atguigu.tingshu.vo.user.UserPaidRecordVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 产品列表API接口
 * </p>
 *
 * 
 */
@FeignClient(value = "service-user", fallback = UserInfoDegradeFeignClient.class)
public interface UserInfoFeignClient {
    /**
     * 根据userId 获取到用户信息
     * @param userId
     * @return
     */
    @GetMapping("api/user/userInfo/getUserInfoVo/{userId}")
    public Result<UserInfoVo> getUserInfoVo(@PathVariable Long userId);
    /**
     * 判断用户是否购买声音列表
     * @param albumId
     * @param trackIdList
     * @return
     */
    @PostMapping("api/user/userInfo/userIsPaidTrack/{albumId}")
    Result<Map<Long, Integer>> userIsPaidTrack(@PathVariable("albumId") Long albumId, @RequestBody List<Long> trackIdList);
    /**
     * 判断用户是否购买专辑
     * @param albumId
     * @return
     */
    @GetMapping("api/user/userInfo/isPaidAlbum/{albumId}")
    public Result<Boolean> isPaidAlbum(@PathVariable("albumId") Long albumId);
    /**
     * 根据专辑ID和用户ID查询用户购买过哪些声音，返回声音的ID列表
     * @param albumId
     * @return
     */
    @GetMapping("api/user/userInfo/findUserPaidTrackList/{albumId}")
    public Result<List<Long>> findUserIsPaidTrack(@PathVariable Long albumId);
    /**
     * 处理用户购买记录
     * @param userPaidRecordVo
     * @return
     */
    @PostMapping("api/user/userInfo/savePaidRecord")
    public Result savePaidRecord(@RequestBody UserPaidRecordVo userPaidRecordVo);
}