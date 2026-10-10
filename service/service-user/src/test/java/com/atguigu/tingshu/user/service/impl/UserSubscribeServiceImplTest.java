package com.atguigu.tingshu.user.service.impl;

import com.atguigu.tingshu.album.client.AlbumInfoFeignClient;
import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import com.atguigu.tingshu.common.rabbit.service.RabbitService;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.model.user.UserSubscribe;
import com.atguigu.tingshu.vo.user.UserSubscribeVo;
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
 * 用户订阅单元测试（Mock MongoTemplate / RabbitService / AlbumInfoFeignClient，不连真实中间件）。
 * 覆盖：集合名分片、切换语义（订阅/取消）、MQ 统计消息（0402 ±1）、列表 VO 组装（含 isFinished 转换）。
 */
@ExtendWith(MockitoExtension.class)
class UserSubscribeServiceImplTest {

    private static final String COLLECTION = "userSubscribe_9";

    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private RabbitService rabbitService;
    @Mock
    private AlbumInfoFeignClient albumInfoFeignClient;
    @InjectMocks
    private UserSubscribeServiceImpl service;

    @Test
    void subscribeAlbum_notSubscribed_createsAndSendsIncMq() {
        when(mongoTemplate.findOne(any(), eq(UserSubscribe.class), eq(COLLECTION))).thenReturn(null);

        assertTrue(service.subscribeAlbum(9L, 100L));

        ArgumentCaptor<UserSubscribe> captor = ArgumentCaptor.forClass(UserSubscribe.class);
        verify(mongoTemplate).save(captor.capture(), eq(COLLECTION));
        UserSubscribe saved = captor.getValue();
        assertNotNull(saved.getId());
        assertEquals(9L, saved.getUserId());
        assertEquals(100L, saved.getAlbumId());
        assertNotNull(saved.getCreateTime());

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitService).sendMessage(eq(MqConst.EXCHANGE_ALBUM), eq(MqConst.ROUTING_ALBUM_STAT_UPDATE), messageCaptor.capture());
        String message = messageCaptor.getValue();
        assertTrue(message.contains("\"albumId\":100"));
        assertTrue(message.contains("\"statType\":\"0402\""));
        assertTrue(message.contains("\"count\":1"));
    }

    @Test
    void subscribeAlbum_alreadySubscribed_removesAndSendsDecMq() {
        UserSubscribe existing = new UserSubscribe();
        existing.setId("mongo-id-1");
        existing.setUserId(9L);
        existing.setAlbumId(100L);
        when(mongoTemplate.findOne(any(), eq(UserSubscribe.class), eq(COLLECTION))).thenReturn(existing);

        assertFalse(service.subscribeAlbum(9L, 100L));

        verify(mongoTemplate).remove(existing, COLLECTION);
        verify(mongoTemplate, never()).save(any(), anyString());

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitService).sendMessage(eq(MqConst.EXCHANGE_ALBUM), eq(MqConst.ROUTING_ALBUM_STAT_UPDATE), messageCaptor.capture());
        assertTrue(messageCaptor.getValue().contains("\"count\":-1"));
    }

    @Test
    void isSubscribeAlbum_anonymous_returnsFalseWithoutQuery() {
        assertFalse(service.isSubscribeAlbum(null, 100L));
        verify(mongoTemplate, never()).exists(any(), eq(UserSubscribe.class), anyString());
    }

    @Test
    void findUserSubscribePage_assemblesAlbumInfoAndConvertsIsFinished() {
        UserSubscribe subscribe = new UserSubscribe();
        subscribe.setId("id-1");
        subscribe.setUserId(9L);
        subscribe.setAlbumId(100L);
        subscribe.setCreateTime(new Date());
        when(mongoTemplate.count(any(), eq(UserSubscribe.class), eq(COLLECTION))).thenReturn(1L);
        when(mongoTemplate.find(any(), eq(UserSubscribe.class), eq(COLLECTION))).thenReturn(List.of(subscribe));

        AlbumInfo albumInfo = new AlbumInfo();
        albumInfo.setId(100L);
        albumInfo.setAlbumTitle("专辑A");
        albumInfo.setCoverUrl("http://cover");
        albumInfo.setIncludeTrackCount(12);
        albumInfo.setIsFinished(1);
        when(albumInfoFeignClient.findAlbumInfoList(anyList())).thenReturn(Result.ok(List.of(albumInfo)));

        IPage<UserSubscribeVo> page = service.findUserSubscribePage(1L, 10L, 9L);

        assertEquals(1L, page.getTotal());
        UserSubscribeVo vo = page.getRecords().get(0);
        assertEquals(100L, vo.getAlbumId());
        assertEquals("专辑A", vo.getAlbumTitle());
        assertEquals("http://cover", vo.getCoverUrl());
        assertEquals(12, vo.getIncludeTrackCount());
        // Integer 1 -> 前端约定的字符串 "1"
        assertEquals("1", vo.getIsFinished());
        assertNotNull(vo.getCreateTime());
    }
}
