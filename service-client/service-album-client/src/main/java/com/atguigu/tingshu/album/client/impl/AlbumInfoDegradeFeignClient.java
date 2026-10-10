package com.atguigu.tingshu.album.client.impl;


import com.atguigu.tingshu.album.client.AlbumInfoFeignClient;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.album.AlbumAttributeValue;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.vo.album.AlbumStatVo;
import org.springframework.stereotype.Component;

import java.util.List;
//降级处理类
@Component
public class AlbumInfoDegradeFeignClient implements AlbumInfoFeignClient {
    //远程调用接口失败后降级方法
    @Override
    public Result<AlbumInfo> getAlbumInfo(Long albumId) {
        return Result.fail();
    }

    @Override
    public Result<List<AlbumAttributeValue>> findAlbumAttributeValue(Long albumId) {
        return Result.fail();
    }

    @Override
    public Result<AlbumStatVo> getAlbumInfoStat(Long albumId) {
        return Result.fail();
    }

    @Override
    public Result<List<AlbumInfo>> findAlbumInfoList(List<Long> albumIds) {
        return Result.fail();
    }
}
