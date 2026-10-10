package com.atguigu.tingshu.album.client;

import com.atguigu.tingshu.album.client.impl.AlbumInfoDegradeFeignClient;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.album.AlbumAttributeValue;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.vo.album.AlbumStatVo;
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
//微服务被远程调用的客户端，指定微服务和降级处理类
@FeignClient(value = "service-album", fallback = AlbumInfoDegradeFeignClient.class)
public interface AlbumInfoFeignClient {
    //远程调用接口中的接口方法的路径要写全
    //根据专辑id获取专辑信息
    @GetMapping("api/album/albumInfo/getAlbumInfo/{albumId}")
    public Result<AlbumInfo> getAlbumInfo(@PathVariable("albumId") Long albumId);

    //获取专辑标签名称和标签值列表
    @GetMapping("api/album/albumInfo/findAlbumAttributeValue/{albumId}")
    Result<List<AlbumAttributeValue>> findAlbumAttributeValue(@PathVariable("albumId") Long albumId);

    //根据专辑id获取四个统计数据
    @GetMapping("api/album/albumInfo/getAlbumInfoStat/{albumId}")
    public Result<AlbumStatVo> getAlbumInfoStat(@PathVariable Long albumId);

    //根据专辑ID列表批量获取专辑信息（供用户服务组装订阅列表等场景，避免 N+1）
    @PostMapping("api/album/albumInfo/findAlbumInfoList")
    public Result<List<AlbumInfo>> findAlbumInfoList(@RequestBody List<Long> albumIds);
}