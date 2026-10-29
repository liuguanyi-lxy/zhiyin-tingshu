package com.atguigu.tingshu.album.service.impl;

import com.atguigu.tingshu.album.mapper.AlbumStatMapper;
import com.atguigu.tingshu.album.mapper.TrackStatMapper;
import com.atguigu.tingshu.album.service.AlbumInfoService;
import com.atguigu.tingshu.vo.album.TrackStatMqVo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 统计原子累加单元测试（纯 Mockito）。
 * 覆盖【修复#3】：原子 SQL 受影响行数 0 视为记录不存在，且专辑统计随声音统计联动更新。
 */
@ExtendWith(MockitoExtension.class)
class AlbumStatUpdateTest {

    @org.junit.jupiter.api.Nested
    class AlbumInfoStat {

        @Mock
        private AlbumStatMapper albumStatMapper;
        @InjectMocks
        private AlbumInfoServiceImpl service;

        @Test
        void incrSucceeds_whenRecordExists() {
            when(albumStatMapper.incrAlbumStatNum(1L, "0401", 1)).thenReturn(1);

            assertDoesNotThrow(() -> service.updateStat(1L, "0401", 1));
        }

        @Test
        void throws_whenStatRecordMissing() {
            when(albumStatMapper.incrAlbumStatNum(99L, "0401", 1)).thenReturn(0);

            assertThrows(IllegalArgumentException.class, () -> service.updateStat(99L, "0401", 1));
        }
    }

    @org.junit.jupiter.api.Nested
    class TrackInfoStat {

        @Mock
        private TrackStatMapper trackStatMapper;
        @Mock
        private AlbumInfoService albumInfoService;
        @InjectMocks
        private TrackInfoServiceImpl service;

        private TrackStatMqVo statVo() {
            TrackStatMqVo vo = new TrackStatMqVo();
            vo.setTrackId(5L);
            vo.setAlbumId(1L);
            // 生产端（播放上报）发送的是声音统计类型 0701，专辑侧由服务层映射为 0401
            vo.setStatType("0701");
            vo.setCount(1);
            return vo;
        }

        @Test
        void trackStatIncr_propagatesToAlbumStatAsPlay() {
            when(trackStatMapper.incrTrackStatNum(5L, "0701", 1)).thenReturn(1);

            service.updateStat(statVo());

            // 声音播放量(0701)联动专辑播放量(0401)
            verify(albumInfoService).updateStat(1L, "0401", 1);
        }

        @Test
        void trackStatMissing_throwsAndSkipsAlbumStat() {
            when(trackStatMapper.incrTrackStatNum(5L, "0701", 1)).thenReturn(0);

            assertThrows(IllegalArgumentException.class, () -> service.updateStat(statVo()));

            verifyNoInteractions(albumInfoService);
        }

        @Test
        void collectStat_doesNotPropagateToAlbumStat() {
            // 收藏统计(0702)只在声音维度累加，专辑维度没有对应统计，不应联动
            TrackStatMqVo collectVo = new TrackStatMqVo();
            collectVo.setTrackId(5L);
            collectVo.setAlbumId(1L);
            collectVo.setStatType("0702");
            collectVo.setCount(1);
            when(trackStatMapper.incrTrackStatNum(5L, "0702", 1)).thenReturn(1);

            service.updateStat(collectVo);

            verifyNoInteractions(albumInfoService);
        }
    }
}
