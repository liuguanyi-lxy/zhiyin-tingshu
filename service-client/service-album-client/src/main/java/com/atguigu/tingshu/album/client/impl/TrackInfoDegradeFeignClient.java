package com.atguigu.tingshu.album.client.impl;


import com.atguigu.tingshu.album.client.TrackInfoFeignClient;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.album.TrackInfo;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TrackInfoDegradeFeignClient implements TrackInfoFeignClient {
    //根据选择本集的声音ID和选择购买的声音数量批量获取下单付费声音列表
    @Override
    public Result<List<TrackInfo>> findPaidTrackInfoList(Long trackId, Integer trackCount) {
        return Result.fail();
    }
}
