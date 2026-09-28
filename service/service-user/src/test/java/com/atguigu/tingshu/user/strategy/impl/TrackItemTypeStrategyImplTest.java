package com.atguigu.tingshu.user.strategy.impl;

import com.atguigu.tingshu.album.client.TrackInfoFeignClient;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.model.user.UserPaidTrack;
import com.atguigu.tingshu.user.mapper.UserPaidTrackMapper;
import com.atguigu.tingshu.vo.user.UserPaidRecordVo;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 购买声音策略单元测试：防重复插入 + 远程取专辑 ID 后逐条落库。
 */
@ExtendWith(MockitoExtension.class)
class TrackItemTypeStrategyImplTest {

    @Mock
    private UserPaidTrackMapper userPaidTrackMapper;
    @Mock
    private TrackInfoFeignClient trackInfoFeignClient;
    @InjectMocks
    private TrackItemTypeStrategyImpl strategy;

    @BeforeAll
    static void initTableMeta() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), UserPaidTrack.class);
    }

    private UserPaidRecordVo recordVo(List<Long> trackIds) {
        UserPaidRecordVo vo = new UserPaidRecordVo();
        vo.setOrderNo("ORDER123");
        vo.setUserId(9L);
        vo.setItemType(SystemConstant.ORDER_ITEM_TYPE_TRACK);
        vo.setItemIdList(trackIds);
        return vo;
    }

    @Test
    void duplicateOrder_skipsFeignAndInsert() {
        when(userPaidTrackMapper.selectCount(any())).thenReturn(1L);

        strategy.savePaidRecord(recordVo(Collections.singletonList(1L)));

        verify(trackInfoFeignClient, never()).getTrackInfo(anyLong());
        verify(userPaidTrackMapper, never()).insert(any(UserPaidTrack.class));
    }

    @Test
    void firstOrder_insertsEachTrackWithFeignedAlbumId() {
        when(userPaidTrackMapper.selectCount(any())).thenReturn(0L);
        TrackInfo trackInfo = new TrackInfo();
        trackInfo.setAlbumId(66L);
        when(trackInfoFeignClient.getTrackInfo(11L)).thenReturn(Result.ok(trackInfo));

        strategy.savePaidRecord(recordVo(Arrays.asList(11L, 12L)));

        ArgumentCaptor<UserPaidTrack> captor = ArgumentCaptor.forClass(UserPaidTrack.class);
        verify(userPaidTrackMapper, times(2)).insert(captor.capture());
        List<UserPaidTrack> inserted = captor.getAllValues();
        assertEquals(11L, inserted.get(0).getTrackId());
        assertEquals(12L, inserted.get(1).getTrackId());
        assertEquals(66L, inserted.get(0).getAlbumId());
        assertEquals(66L, inserted.get(1).getAlbumId());
        assertEquals(9L, inserted.get(0).getUserId());
    }
}
