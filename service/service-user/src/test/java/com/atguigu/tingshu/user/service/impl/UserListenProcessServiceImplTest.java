package com.atguigu.tingshu.user.service.impl;

import com.atguigu.tingshu.album.client.AlbumInfoFeignClient;
import com.atguigu.tingshu.album.client.TrackInfoFeignClient;
import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import com.atguigu.tingshu.common.rabbit.service.RabbitService;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.model.user.UserListenProcess;
import com.atguigu.tingshu.vo.user.UserListenProcessListVo;
import com.atguigu.tingshu.vo.user.UserListenProcessVo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 播放进度单元测试（Mock MongoTemplate / RedisTemplate / RabbitService，不连真实中间件）。
 * 覆盖：进度查询、进度新建/更新、Redis bitmap 去重后的播放量 MQ。
 */
@ExtendWith(MockitoExtension.class)
class UserListenProcessServiceImplTest {

    private static final String COLLECTION = "userListenProcess_9";

    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private RedisTemplate redisTemplate;
    @Mock
    private ValueOperations valueOperations;
    @Mock
    private RabbitService rabbitService;
    @Mock
    private TrackInfoFeignClient trackInfoFeignClient;
    @Mock
    private AlbumInfoFeignClient albumInfoFeignClient;
    @InjectMocks
    private UserListenProcessServiceImpl service;

    private UserListenProcessVo vo(Long trackId, String breakSecond) {
        UserListenProcessVo vo = new UserListenProcessVo();
        vo.setAlbumId(66L);
        vo.setTrackId(trackId);
        vo.setBreakSecond(new BigDecimal(breakSecond));
        return vo;
    }

    @Test
    void getTrackBreakSecond_found_returnsBreakSecond() {
        UserListenProcess process = new UserListenProcess();
        process.setBreakSecond(new BigDecimal("25.5"));
        when(mongoTemplate.findOne(any(), eq(UserListenProcess.class), eq(COLLECTION))).thenReturn(process);

        assertEquals(new BigDecimal("25.5"), service.getTrackBreakSecond(9L, 1L));
    }

    @Test
    void getTrackBreakSecond_missing_returnsZero() {
        when(mongoTemplate.findOne(any(), eq(UserListenProcess.class), eq(COLLECTION))).thenReturn(null);

        assertEquals(BigDecimal.ZERO, service.getTrackBreakSecond(9L, 1L));
    }

    @Test
    void saveOrUpdate_existingProcess_updatesInPlaceWithoutMq() {
        UserListenProcess existing = new UserListenProcess();
        existing.setId("mongo-id-1");
        existing.setUserId(9L);
        existing.setBreakSecond(BigDecimal.ONE);
        when(mongoTemplate.findOne(any(), eq(UserListenProcess.class), eq(COLLECTION))).thenReturn(existing);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setBit("user:9", 1L, true)).thenReturn(true);

        service.saveOrUpdateListenProcess(9L, vo(1L, "30"));

        // 原实体被原位更新后 save
        ArgumentCaptor<UserListenProcess> captor = ArgumentCaptor.forClass(UserListenProcess.class);
        verify(mongoTemplate).save(captor.capture(), eq(COLLECTION));
        assertEquals("mongo-id-1", captor.getValue().getId());
        assertEquals(new BigDecimal("30"), captor.getValue().getBreakSecond());
        // 24 小时内已计过播放量：不再发 MQ
        verify(rabbitService, never()).sendMessage(anyString(), anyString(), anyString());
    }

    @Test
    void saveOrUpdate_newProcess_createsWithDefaults() {
        when(mongoTemplate.findOne(any(), eq(UserListenProcess.class), eq(COLLECTION))).thenReturn(null);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setBit("user:9", 2L, true)).thenReturn(true);

        service.saveOrUpdateListenProcess(9L, vo(2L, "12.5"));

        ArgumentCaptor<UserListenProcess> captor = ArgumentCaptor.forClass(UserListenProcess.class);
        verify(mongoTemplate).save(captor.capture(), eq(COLLECTION));
        UserListenProcess saved = captor.getValue();
        assertNotNull(saved.getId());
        assertEquals(9L, saved.getUserId());
        assertEquals(2L, saved.getTrackId());
        assertEquals(66L, saved.getAlbumId());
        assertEquals(new BigDecimal("12.5"), saved.getBreakSecond());
        assertEquals(1, saved.getIsShow());
        assertNotNull(saved.getCreateTime());
        assertNotNull(saved.getUpdateTime());
        verify(rabbitService, never()).sendMessage(anyString(), anyString(), anyString());
    }

    @Test
    void firstPlayIn24h_sendsTrackStatMqAndSetsExpire() {
        when(mongoTemplate.findOne(any(), eq(UserListenProcess.class), eq(COLLECTION))).thenReturn(null);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        // 第一次 setBit 返回 false 表示 24 小时内没有播放过 → 计播放量
        when(valueOperations.setBit(eq("user:9"), anyLong(), eq(true))).thenReturn(false, true);

        service.saveOrUpdateListenProcess(9L, vo(3L, "5"));

        verify(redisTemplate).expire("user:9", 24 * 60 * 60, TimeUnit.SECONDS);
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitService).sendMessage(eq(MqConst.EXCHANGE_TRACK), eq(MqConst.ROUTING_TRACK_STAT_UPDATE), messageCaptor.capture());
        String message = messageCaptor.getValue();
        assertTrue(message.contains("\"trackId\":3"));
        assertTrue(message.contains("\"albumId\":66"));
        assertTrue(message.contains("\"statType\":\"0701\""));
        assertTrue(message.contains("\"count\":1"));
    }

    @Test
    void findUserPage_assemblesVoWithPlayRateAndCoverFallback() {
        UserListenProcess process = new UserListenProcess();
        process.setId("mongo-id-1");
        process.setUserId(9L);
        process.setAlbumId(66L);
        process.setTrackId(1L);
        process.setBreakSecond(new BigDecimal("25.5"));
        when(mongoTemplate.count(any(), eq(UserListenProcess.class), eq(COLLECTION))).thenReturn(1L);
        when(mongoTemplate.find(any(), eq(UserListenProcess.class), eq(COLLECTION))).thenReturn(List.of(process));

        TrackInfo trackInfo = new TrackInfo();
        trackInfo.setId(1L);
        trackInfo.setAlbumId(66L);
        trackInfo.setTrackTitle("第1集");
        trackInfo.setCoverUrl(null);
        trackInfo.setMediaDuration(51.0F);
        when(trackInfoFeignClient.findTrackInfoListByIds(anyList())).thenReturn(Result.ok(List.of(trackInfo)));

        AlbumInfo albumInfo = new AlbumInfo();
        albumInfo.setId(66L);
        albumInfo.setAlbumTitle("专辑A");
        albumInfo.setCoverUrl("http://album-cover");
        when(albumInfoFeignClient.findAlbumInfoList(anyList())).thenReturn(Result.ok(List.of(albumInfo)));

        IPage<UserListenProcessListVo> page = service.findUserPage(1L, 10L, 9L);

        assertEquals(1L, page.getTotal());
        UserListenProcessListVo vo = page.getRecords().get(0);
        assertEquals("mongo-id-1", vo.getId());
        assertEquals(1L, vo.getTrackId());
        assertEquals("第1集", vo.getTrackTitle());
        assertEquals("专辑A", vo.getAlbumTitle());
        // 声音无独立封面时回退使用专辑封面
        assertEquals("http://album-cover", vo.getCoverUrl());
        assertEquals(0, new BigDecimal("51.0").compareTo(vo.getMediaDuration()));
        // 25.5/51 ≈ 50%
        assertEquals("50%", vo.getPlayRate());
    }

    @Test
    void deleteListenProcess_softDeletesWithUserGuard() {
        service.deleteListenProcess(9L, "mongo-id-1");

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateFirst(queryCaptor.capture(), updateCaptor.capture(), eq(UserListenProcess.class), eq(COLLECTION));
        // 查询同时限定文档id与用户id，防止越权删除他人记录
        assertEquals("mongo-id-1", queryCaptor.getValue().getQueryObject().get("_id"));
        assertEquals(9L, queryCaptor.getValue().getQueryObject().get("userId"));
        // 软删除：$set isShow=0，保留断点数据
        assertEquals(0, ((Document) updateCaptor.getValue().getUpdateObject().get("$set")).get("isShow"));
    }

    @Test
    void getLatelyTrack_missing_returnsEmptyObjectNotNull() {
        when(mongoTemplate.findOne(any(), eq(UserListenProcess.class), eq(COLLECTION))).thenReturn(null);

        UserListenProcess latest = service.getLatelyTrack(9L);

        // 返回空对象而不是 null，避免前端从 null 解构字段报错
        assertNotNull(latest);
        assertNull(latest.getTrackId());
    }
}
