package com.atguigu.tingshu.album.client;

import com.atguigu.tingshu.album.client.impl.TrackInfoDegradeFeignClient;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.album.TrackInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * <p>
 * 产品列表API接口
 * </p>
 *
 */
@FeignClient(value = "service-album", fallback = TrackInfoDegradeFeignClient.class)
public interface TrackInfoFeignClient {
    //根据选择本集的声音ID和选择购买的声音数量批量获取下单付费声音列表
    @GetMapping("api/album/trackInfo/findPaidTrackInfoList/{trackId}/{trackCount}")
    public Result<List<TrackInfo>> findPaidTrackInfoList(@PathVariable Long trackId, @PathVariable Integer trackCount);
    /**
     * 根据声音ID获取声音信息
     * @param trackId
     * @return
     */
    @GetMapping("api/album/trackInfo/getTrackInfo/{trackId}")
    public Result getTrackInfo(@PathVariable("trackId") Long trackId);

    //根据声音ID列表批量获取声音信息（供用户服务组装收藏/历史列表等场景，避免 N+1）
    @PostMapping("api/album/trackInfo/findTrackInfoListByIds")
    public Result<List<TrackInfo>> findTrackInfoListByIds(@RequestBody List<Long> trackIds);
}