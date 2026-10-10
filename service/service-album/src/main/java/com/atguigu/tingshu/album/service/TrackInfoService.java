package com.atguigu.tingshu.album.service;

import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.query.album.TrackInfoQuery;
import com.atguigu.tingshu.vo.album.AlbumTrackListVo;
import com.atguigu.tingshu.vo.album.TrackInfoVo;
import com.atguigu.tingshu.vo.album.TrackListVo;
import com.atguigu.tingshu.vo.album.TrackStatMqVo;
import com.atguigu.tingshu.vo.album.TrackStatVo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

public interface TrackInfoService extends IService<TrackInfo> {
    //保存声音信息
    void saveTrackInfo(TrackInfoVo trackInfoVo);
    //根据分页条件和查询条件查询用户的声音列表
    IPage<TrackListVo> findUserTrackPage(Page<TrackListVo> pageParam, TrackInfoQuery trackInfoQuery);
    //根据声音ID删除声音
    void removeTrackInfo(Long id);
    //修改声音信息
    void updateTrackInfo(Long trackId, TrackInfoVo trackInfoVo);
    //根据专辑ID查询声音列表
    IPage<AlbumTrackListVo> findAlbumTrackPage(Page<AlbumTrackListVo> pageParam, Long albumId, Long userId);
    //更新声音播放量
    void updateStat(TrackStatMqVo trackStatMqVo);
    //购买声音时显示可以购买声音的各种套餐类型，比如本集、后十集、后二十集之类的
    List<Map<String, Object>> findUserTrackPaidList(Long trackId);
    //根据选择本集的声音ID和选择购买的声音数量批量获取下单付费声音列表
    List<TrackInfo> findPaidTrackInfoList(Long trackId, Integer trackCount);

    //根据声音ID列表批量查询声音信息（供收藏列表、收听历史等场景组装数据，避免逐条 Feign 的 N+1）
    List<TrackInfo> findTrackInfoListByIds(List<Long> trackIds);

    //根据声音ID获取四个统计数据（播放/收藏/点赞/评论），供播放页展示
    TrackStatVo getTrackStatVo(Long trackId);
}
