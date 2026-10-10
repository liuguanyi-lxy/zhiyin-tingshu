package com.atguigu.tingshu.user.service.impl;

import com.atguigu.tingshu.album.client.TrackInfoFeignClient;
import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import com.atguigu.tingshu.common.rabbit.service.RabbitService;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.model.user.UserCollect;
import com.atguigu.tingshu.vo.user.UserCollectVo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户收藏单元测试（Mock MongoTemplate / RabbitService / TrackInfoFeignClient，不连真实中间件）。
 * 覆盖：集合名分片、切换语义（收藏/取消）、MQ 统计消息（0702 ±1，含专辑ID归属）、列表 VO 组装。
 */
@ExtendWith(MockitoExtension.class)
class UserCollectServiceImplTest {

    private static final String COLLECTION = "userCollect_9";

    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private RabbitService rabbitService;
    @Mock
    private TrackInfoFeignClient trackInfoFeignClient;
    @InjectMocks
    private UserCollectServiceImpl service;

    private TrackInfo trackInfo(Long trackId, Long albumId) {
        TrackInfo trackInfo = new TrackInfo();
        trackInfo.setId(trackId);
        trackInfo.setAlbumId(albumId);
        trackInfo.setTrackTitle("声音标题");
        trackInfo.setCoverUrl("http://track-cover");
        trackInfo.setMediaDuration(51.0F);
        return trackInfo;
    }

    @Test
    void collectTrack_notCollected_createsAndSendsIncMq() {
        when(mongoTemplate.findOne(any(), eq(UserCollect.class), eq(COLLECTION))).thenReturn(null);
        when(trackInfoFeignClient.getTrackInfo(100L)).thenReturn(Result.ok(trackInfo(100L, 66L)));

        assertTrue(service.collectTrack(9L, 100L));

        ArgumentCaptor<UserCollect> captor = ArgumentCaptor.forClass(UserCollect.class);
        verify(mongoTemplate).save(captor.capture(), eq(COLLECTION));
        UserCollect saved = captor.getValue();
        assertNotNull(saved.getId());
        assertEquals(9L, saved.getUserId());
        assertEquals(100L, saved.getTrackId());
        assertNotNull(saved.getCreateTime());

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitService).sendMessage(eq(MqConst.EXCHANGE_TRACK), eq(MqConst.ROUTING_TRACK_STAT_UPDATE), messageCaptor.capture());
        String message = messageCaptor.getValue();
        assertTrue(message.contains("\"trackId\":100"));
        assertTrue(message.contains("\"albumId\":66"));
        assertTrue(message.contains("\"statType\":\"0702\""));
        assertTrue(message.contains("\"count\":1"));
    }

    @Test
    void collectTrack_alreadyCollected_removesAndSendsDecMq() {
        UserCollect existing = new UserCollect();
        existing.setId("mongo-id-1");
        existing.setUserId(9L);
        existing.setTrackId(100L);
        when(mongoTemplate.findOne(any(), eq(UserCollect.class), eq(COLLECTION))).thenReturn(existing);
        when(trackInfoFeignClient.getTrackInfo(100L)).thenReturn(Result.ok(trackInfo(100L, 66L)));

        assertFalse(service.collectTrack(9L, 100L));

        verify(mongoTemplate).remove(existing, COLLECTION);
        verify(mongoTemplate, never()).save(any(), anyString());

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitService).sendMessage(eq(MqConst.EXCHANGE_TRACK), eq(MqConst.ROUTING_TRACK_STAT_UPDATE), messageCaptor.capture());
        assertTrue(messageCaptor.getValue().contains("\"count\":-1"));
    }

    @Test
    void isCollectTrack_anonymous_returnsFalseWithoutQuery() {
        assertFalse(service.isCollectTrack(null, 100L));
        verify(mongoTemplate, never()).exists(any(), eq(UserCollect.class), anyString());
    }

    @Test
    void findUserCollectPage_assemblesTrackInfo() {
        UserCollect collect = new UserCollect();
        collect.setId("id-1");
        collect.setUserId(9L);
        collect.setTrackId(100L);
        collect.setCreateTime(new Date());
        when(mongoTemplate.count(any(), eq(UserCollect.class), eq(COLLECTION))).thenReturn(1L);
        when(mongoTemplate.find(any(), eq(UserCollect.class), eq(COLLECTION))).thenReturn(List.of(collect));
        when(trackInfoFeignClient.findTrackInfoListByIds(anyList())).thenReturn(Result.ok(List.of(trackInfo(100L, 66L))));

        IPage<UserCollectVo> page = service.findUserCollectPage(1L, 10L, 9L);

        assertEquals(1L, page.getTotal());
        UserCollectVo vo = page.getRecords().get(0);
        assertEquals(100L, vo.getTrackId());
        assertEquals(66L, vo.getAlbumId());
        assertEquals("声音标题", vo.getTrackTitle());
        assertEquals("http://track-cover", vo.getCoverUrl());
        assertNotNull(vo.getCreateTime());
    }
}
