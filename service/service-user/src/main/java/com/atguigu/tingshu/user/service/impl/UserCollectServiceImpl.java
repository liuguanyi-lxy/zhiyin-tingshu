package com.atguigu.tingshu.user.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.tingshu.album.client.TrackInfoFeignClient;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import com.atguigu.tingshu.common.rabbit.service.RabbitService;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.util.MongoUtil;
import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.model.user.UserCollect;
import com.atguigu.tingshu.user.service.UserCollectService;
import com.atguigu.tingshu.vo.album.TrackStatMqVo;
import com.atguigu.tingshu.vo.user.UserCollectVo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用户收藏业务实现（MongoDB 分片集合 userCollect_{userId%100}，收藏对象为声音）。
 *
 * <p>设计要点：
 * 1. 收藏/取消收藏是"切换"语义（播放页同一按钮来回切），返回切换后的状态；
 * 2. 每次切换通过 MQ 异步更新声音收藏量统计（0702，±1），消费端 businessNo 幂等防重；
 * 3. 列表页一次批量 Feign 拉取声音信息组装 VO，避免逐条调用的 N+1。</p>
 */
@Slf4j
@Service
@SuppressWarnings({"all"})
public class UserCollectServiceImpl implements UserCollectService {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private RabbitService rabbitService;

    @Autowired
    private TrackInfoFeignClient trackInfoFeignClient;

    private String collectionName(Long userId) {
        return MongoUtil.getCollectionName(MongoUtil.MongoCollectionEnum.USER_COLLECT, userId);
    }

    @Override
    public Boolean collectTrack(Long userId, Long trackId) {
        String collectionName = collectionName(userId);
        Query query = new Query(Criteria.where("userId").is(userId).and("trackId").is(trackId));
        UserCollect existing = mongoTemplate.findOne(query, UserCollect.class, collectionName);
        boolean collected;
        if (existing != null) {
            //已收藏 -> 取消收藏
            mongoTemplate.remove(existing, collectionName);
            collected = false;
        } else {
            //未收藏 -> 收藏（先查后写，同一用户并发双击存在极小竞态，开发环境可接受）
            UserCollect collect = new UserCollect();
            collect.setId(ObjectId.get().toString());
            collect.setUserId(userId);
            collect.setTrackId(trackId);
            collect.setCreateTime(new Date());
            mongoTemplate.save(collect, collectionName);
            collected = true;
        }
        //异步更新声音收藏量（0702，±1）
        sendTrackCollectStat(trackId, collected ? 1 : -1);
        log.info("[收藏] 用户{} {}声音{}", userId, collected ? "收藏" : "取消收藏", trackId);
        return collected;
    }

    private void sendTrackCollectStat(Long trackId, int count) {
        try {
            //统计消息里带专辑ID：查一次声音信息获取归属专辑（失败时置空，消费端按声音ID照样能累加）
            Long albumId = null;
            try {
                Result trackResult = trackInfoFeignClient.getTrackInfo(trackId);
                if (trackResult != null && trackResult.getData() != null) {
                    albumId = ((TrackInfo) trackResult.getData()).getAlbumId();
                }
            } catch (Exception e) {
                log.warn("[收藏] 获取声音信息失败，统计消息将不带专辑ID trackId={}", trackId, e);
            }
            TrackStatMqVo statMqVo = new TrackStatMqVo();
            statMqVo.setBusinessNo(UUID.randomUUID().toString());
            statMqVo.setAlbumId(albumId);
            statMqVo.setTrackId(trackId);
            statMqVo.setStatType(SystemConstant.TRACK_STAT_COLLECT);
            statMqVo.setCount(count);
            rabbitService.sendMessage(MqConst.EXCHANGE_TRACK, MqConst.ROUTING_TRACK_STAT_UPDATE, JSON.toJSONString(statMqVo));
        } catch (Exception e) {
            log.error("[收藏] 发送收藏量统计消息失败 trackId={}, count={}", trackId, count, e);
        }
    }

    @Override
    public Boolean isCollectTrack(Long userId, Long trackId) {
        if (userId == null) {
            //播放页允许匿名访问，未登录一律视为未收藏
            return false;
        }
        return mongoTemplate.exists(new Query(Criteria.where("userId").is(userId).and("trackId").is(trackId)),
                UserCollect.class, collectionName(userId));
    }

    @Override
    public IPage<UserCollectVo> findUserCollectPage(Long page, Long limit, Long userId) {
        String collectionName = collectionName(userId);
        Criteria criteria = Criteria.where("userId").is(userId);
        long total = mongoTemplate.count(new Query(criteria), UserCollect.class, collectionName);

        Query query = new Query(criteria)
                .with(Sort.by(Sort.Direction.DESC, "createTime"))
                .skip((page - 1) * limit)
                .limit(limit.intValue());
        List<UserCollect> collects = mongoTemplate.find(query, UserCollect.class, collectionName);

        Page<UserCollectVo> pageModel = new Page<>(page, limit);
        pageModel.setTotal(total);
        if (CollectionUtils.isEmpty(collects)) {
            return pageModel;
        }
        //一次批量拉取声音信息，避免逐条 Feign 的 N+1
        List<Long> trackIds = collects.stream().map(UserCollect::getTrackId).collect(Collectors.toList());
        Result<List<TrackInfo>> trackResult = trackInfoFeignClient.findTrackInfoListByIds(trackIds);
        Map<Long, TrackInfo> trackMap = (trackResult == null || CollectionUtils.isEmpty(trackResult.getData()))
                ? Collections.emptyMap()
                : trackResult.getData().stream()
                        .collect(Collectors.toMap(TrackInfo::getId, Function.identity(), (a, b) -> a));

        List<UserCollectVo> records = collects.stream().map(collect -> {
            UserCollectVo vo = new UserCollectVo();
            vo.setTrackId(collect.getTrackId());
            vo.setCreateTime(collect.getCreateTime());
            TrackInfo trackInfo = trackMap.get(collect.getTrackId());
            if (trackInfo != null) {
                vo.setAlbumId(trackInfo.getAlbumId());
                vo.setTrackTitle(trackInfo.getTrackTitle());
                vo.setCoverUrl(trackInfo.getCoverUrl());
            }
            return vo;
        }).collect(Collectors.toList());
        pageModel.setRecords(records);
        return pageModel;
    }
}
