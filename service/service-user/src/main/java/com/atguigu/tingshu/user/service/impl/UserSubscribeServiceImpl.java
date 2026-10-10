package com.atguigu.tingshu.user.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.tingshu.album.client.AlbumInfoFeignClient;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import com.atguigu.tingshu.common.rabbit.service.RabbitService;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.util.MongoUtil;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.model.user.UserSubscribe;
import com.atguigu.tingshu.user.service.UserSubscribeService;
import com.atguigu.tingshu.vo.album.AlbumStatMqVo;
import com.atguigu.tingshu.vo.user.UserSubscribeVo;
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
 * 用户订阅业务实现（MongoDB 分片集合 userSubscribe_{userId%100}）。
 *
 * <p>设计要点：
 * 1. 订阅/取消订阅是"切换"语义（前端详情页同一按钮来回切），返回切换后的状态；
 * 2. 每次切换通过 MQ 异步更新专辑订阅量统计（0402，订阅+1/取消-1），消费端 businessNo 幂等防重；
 * 3. 列表页一次批量 Feign 拉取专辑信息组装 VO，避免逐条调用的 N+1。</p>
 */
@Slf4j
@Service
@SuppressWarnings({"all"})
public class UserSubscribeServiceImpl implements UserSubscribeService {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private RabbitService rabbitService;

    @Autowired
    private AlbumInfoFeignClient albumInfoFeignClient;

    private String collectionName(Long userId) {
        return MongoUtil.getCollectionName(MongoUtil.MongoCollectionEnum.USER_SUBSCRIBE, userId);
    }

    @Override
    public Boolean subscribeAlbum(Long userId, Long albumId) {
        String collectionName = collectionName(userId);
        Query query = new Query(Criteria.where("userId").is(userId).and("albumId").is(albumId));
        UserSubscribe existing = mongoTemplate.findOne(query, UserSubscribe.class, collectionName);
        boolean subscribed;
        if (existing != null) {
            //已订阅 -> 取消订阅
            mongoTemplate.remove(existing, collectionName);
            subscribed = false;
        } else {
            //未订阅 -> 订阅（先查后写，同一用户并发双击存在极小竞态，开发环境可接受）
            UserSubscribe subscribe = new UserSubscribe();
            subscribe.setId(ObjectId.get().toString());
            subscribe.setUserId(userId);
            subscribe.setAlbumId(albumId);
            subscribe.setCreateTime(new Date());
            mongoTemplate.save(subscribe, collectionName);
            subscribed = true;
        }
        //异步更新专辑订阅量（0402，±1）；统计失败不影响订阅主流程
        sendAlbumSubscribeStat(albumId, subscribed ? 1 : -1);
        log.info("[订阅] 用户{} {}专辑{}", userId, subscribed ? "订阅" : "取消订阅", albumId);
        return subscribed;
    }

    private void sendAlbumSubscribeStat(Long albumId, int count) {
        try {
            AlbumStatMqVo statMqVo = new AlbumStatMqVo();
            statMqVo.setAlbumId(albumId);
            statMqVo.setStatType(SystemConstant.ALBUM_STAT_SUBSCRIBE);
            statMqVo.setCount(count);
            statMqVo.setBusinessNo(UUID.randomUUID().toString());
            rabbitService.sendMessage(MqConst.EXCHANGE_ALBUM, MqConst.ROUTING_ALBUM_STAT_UPDATE, JSON.toJSONString(statMqVo));
        } catch (Exception e) {
            log.error("[订阅] 发送订阅量统计消息失败 albumId={}, count={}", albumId, count, e);
        }
    }

    @Override
    public Boolean isSubscribeAlbum(Long userId, Long albumId) {
        if (userId == null) {
            //专辑详情页允许匿名访问，未登录一律视为未订阅
            return false;
        }
        return mongoTemplate.exists(new Query(Criteria.where("userId").is(userId).and("albumId").is(albumId)),
                UserSubscribe.class, collectionName(userId));
    }

    @Override
    public IPage<UserSubscribeVo> findUserSubscribePage(Long page, Long limit, Long userId) {
        String collectionName = collectionName(userId);
        Criteria criteria = Criteria.where("userId").is(userId);
        long total = mongoTemplate.count(new Query(criteria), UserSubscribe.class, collectionName);

        Query query = new Query(criteria)
                .with(Sort.by(Sort.Direction.DESC, "createTime"))
                .skip((page - 1) * limit)
                .limit(limit.intValue());
        List<UserSubscribe> subscribes = mongoTemplate.find(query, UserSubscribe.class, collectionName);

        Page<UserSubscribeVo> pageModel = new Page<>(page, limit);
        pageModel.setTotal(total);
        if (CollectionUtils.isEmpty(subscribes)) {
            return pageModel;
        }
        //一次批量拉取专辑信息，避免逐条 Feign 的 N+1
        List<Long> albumIds = subscribes.stream().map(UserSubscribe::getAlbumId).collect(Collectors.toList());
        Result<List<AlbumInfo>> albumResult = albumInfoFeignClient.findAlbumInfoList(albumIds);
        Map<Long, AlbumInfo> albumMap = (albumResult == null || CollectionUtils.isEmpty(albumResult.getData()))
                ? Collections.emptyMap()
                : albumResult.getData().stream()
                        .collect(Collectors.toMap(AlbumInfo::getId, Function.identity(), (a, b) -> a));

        List<UserSubscribeVo> records = subscribes.stream().map(subscribe -> {
            UserSubscribeVo vo = new UserSubscribeVo();
            vo.setAlbumId(subscribe.getAlbumId());
            vo.setCreateTime(subscribe.getCreateTime());
            AlbumInfo albumInfo = albumMap.get(subscribe.getAlbumId());
            if (albumInfo != null) {
                vo.setAlbumTitle(albumInfo.getAlbumTitle());
                vo.setCoverUrl(albumInfo.getCoverUrl());
                vo.setIncludeTrackCount(albumInfo.getIncludeTrackCount());
                //前端按字符串比较 isFinished（"0"未完结/"1"完结），实体侧是 Integer，这里做转换
                vo.setIsFinished(albumInfo.getIsFinished() == null ? "0" : String.valueOf(albumInfo.getIsFinished()));
            }
            return vo;
        }).collect(Collectors.toList());
        pageModel.setRecords(records);
        return pageModel;
    }
}
