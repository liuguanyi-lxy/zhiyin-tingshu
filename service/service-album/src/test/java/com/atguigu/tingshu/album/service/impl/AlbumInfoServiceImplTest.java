package com.atguigu.tingshu.album.service.impl;

import com.atguigu.tingshu.album.mapper.AlbumAttributeValueMapper;
import com.atguigu.tingshu.album.mapper.AlbumInfoMapper;
import com.atguigu.tingshu.album.mapper.AlbumStatMapper;
import com.atguigu.tingshu.album.mapper.TrackInfoMapper;
import com.atguigu.tingshu.album.service.AlbumAttributeValueService;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import com.atguigu.tingshu.common.rabbit.service.RabbitService;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.model.album.AlbumAttributeValue;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.model.album.AlbumStat;
import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.vo.album.AlbumAttributeValueVo;
import com.atguigu.tingshu.vo.album.AlbumInfoVo;
import com.atguigu.tingshu.vo.album.AlbumStatVo;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 专辑业务单元测试（纯 Mockito）。
 * 覆盖：专辑查询/组装、专辑更新（含上下架 MQ 分支）、专辑删除（含声音校验）、专辑保存（试听集数/统计初始化/上架 MQ）。
 * updateStat 原子累加已被 AlbumStatUpdateTest 覆盖，此处不再重复。
 */
@ExtendWith(MockitoExtension.class)
class AlbumInfoServiceImplTest {

    @Mock
    private AlbumInfoMapper albumInfoMapper;
    @Mock
    private AlbumStatMapper albumStatMapper;
    @Mock
    private AlbumAttributeValueMapper albumAttributeValueMapper;
    @Mock
    private AlbumAttributeValueService albumAttributeValueService;
    @Mock
    private TrackInfoMapper trackInfoMapper;
    @Mock
    private RabbitService rabbitService;
    @InjectMocks
    private AlbumInfoServiceImpl service;

    @BeforeAll
    static void initTableMeta() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AlbumInfo.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AlbumAttributeValue.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AlbumStat.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), TrackInfo.class);
    }

    @BeforeEach
    void injectBaseMapper() throws Exception {
        Field baseMapper = com.baomidou.mybatisplus.extension.service.impl.ServiceImpl.class.getDeclaredField("baseMapper");
        baseMapper.setAccessible(true);
        baseMapper.set(service, albumInfoMapper);
    }

    @AfterEach
    void clearAuthContext() {
        AuthContextHolder.removeUserId();
    }

    private AlbumInfo album(String isOpen) {
        AlbumInfo albumInfo = new AlbumInfo();
        albumInfo.setId(66L);
        albumInfo.setAlbumTitle("老专辑");
        albumInfo.setIsOpen(isOpen);
        return albumInfo;
    }

    private AlbumInfoVo albumVo(String isOpen, List<AlbumAttributeValueVo> attributes) {
        AlbumInfoVo vo = new AlbumInfoVo();
        vo.setAlbumTitle("新专辑");
        vo.setIsOpen(isOpen);
        vo.setAlbumAttributeValueVoList(attributes);
        return vo;
    }

    private AlbumAttributeValueVo attributeVo() {
        AlbumAttributeValueVo vo = new AlbumAttributeValueVo();
        vo.setAttributeId(1L);
        vo.setValueId(11L);
        return vo;
    }

    @Nested
    class QueryMethods {

        @Test
        void findAlbumAttributeValueByAlbumId_delegatesToMapper() {
            AlbumAttributeValue attribute = new AlbumAttributeValue();
            when(albumAttributeValueMapper.selectList(any())).thenReturn(Collections.singletonList(attribute));

            List<AlbumAttributeValue> result = service.findAlbumAttributeValueByAlbumId(66L);

            assertEquals(1, result.size());
        }

        @Test
        void getAlbumInfoStat_delegatesToMapper() {
            AlbumStatVo statVo = new AlbumStatVo();
            when(albumStatMapper.getAlbumInfoStat(66L)).thenReturn(statVo);

            assertEquals(statVo, service.getAlbumInfoStat(66L));
        }

        @Test
        void findUserAlbumList_returnsPageRecords() {
            Page<AlbumInfo> page = new Page<>(1, 100);
            page.setRecords(Collections.singletonList(album("1")));
            when(albumInfoMapper.selectPage(any(), any())).thenReturn(page);

            List<AlbumInfo> result = service.findUserAlbumList(9L);

            assertEquals(1, result.size());
            assertEquals(66L, result.get(0).getId());
        }

        @Test
        void getAlbumInfo_assemblesAttributeList() {
            AlbumInfo albumInfo = album("1");
            when(albumInfoMapper.selectById(66L)).thenReturn(albumInfo);
            AlbumAttributeValue attribute = new AlbumAttributeValue();
            when(albumAttributeValueMapper.selectList(any())).thenReturn(Collections.singletonList(attribute));

            AlbumInfo result = service.getAlbumInfo(66L);

            assertEquals(1, result.getAlbumAttributeValueList().size());
        }
    }

    @Nested
    class UpdateAlbum {

        @Test
        void privateToPublic_sendsUpperMqAndRebuildsAttributes() {
            when(albumInfoMapper.selectById(66L)).thenReturn(album("0"));
            when(albumAttributeValueMapper.delete(any())).thenReturn(1);

            service.updateAlbumInfo(66L, albumVo("1", Collections.singletonList(attributeVo())));

            verify(albumInfoMapper).updateById(any(AlbumInfo.class));
            verify(albumAttributeValueMapper).delete(any());
            ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
            verify(albumAttributeValueService).saveBatch(captor.capture());
            assertEquals(66L, ((List<AlbumAttributeValue>) captor.getValue()).get(0).getAlbumId());
            verify(rabbitService).sendMessage(MqConst.EXCHANGE_ALBUM, MqConst.ROUTING_ALBUM_UPPER, 66L);
        }

        @Test
        void publicToPrivate_sendsLowerMq() {
            when(albumInfoMapper.selectById(66L)).thenReturn(album("1"));
            when(albumAttributeValueMapper.delete(any())).thenReturn(1);

            service.updateAlbumInfo(66L, albumVo("0", null));

            verify(rabbitService).sendMessage(MqConst.EXCHANGE_ALBUM, MqConst.ROUTING_ALBUM_LOWER, 66L);
            verify(albumAttributeValueService, never()).saveBatch(anyList());
        }

        @Test
        void visibilityUnchanged_sendsNoMq() {
            when(albumInfoMapper.selectById(66L)).thenReturn(album("1"));
            when(albumAttributeValueMapper.delete(any())).thenReturn(1);

            service.updateAlbumInfo(66L, albumVo("1", null));

            verify(rabbitService, never()).sendMessage(anyString(), anyString(), any());
        }
    }

    @Nested
    class RemoveAlbum {

        @Test
        void albumContainsTracks_throwsAndDeletesNothing() {
            when(trackInfoMapper.selectCount(any())).thenReturn(1L);

            assertThrows(RuntimeException.class, () -> service.removeAlbumInfo("66"));

            verify(albumInfoMapper, never()).deleteById(anyLong());
            verify(albumStatMapper, never()).delete(any());
            verify(rabbitService, never()).sendMessage(anyString(), anyString(), any());
        }

        @Test
        void emptyAlbum_removesAllRelatedDataAndSendsLowerMq() {
            when(trackInfoMapper.selectCount(any())).thenReturn(0L);
            // 主代码把 String 类型的 albumId 传给 deleteById
            when(albumInfoMapper.deleteById("66")).thenReturn(1);
            when(albumAttributeValueMapper.delete(any())).thenReturn(1);
            when(albumStatMapper.delete(any())).thenReturn(1);

            service.removeAlbumInfo("66");

            verify(albumInfoMapper).deleteById("66");
            verify(albumAttributeValueMapper).delete(any());
            verify(albumStatMapper).delete(any());
            verify(rabbitService).sendMessage(MqConst.EXCHANGE_ALBUM, MqConst.ROUTING_ALBUM_LOWER, "66");
        }
    }

    @Nested
    class SaveAlbum {

        @Test
        void paidAlbum_forcesThreeFreeTracksAndSendsUpperMqWhenOpen() {
            AuthContextHolder.setUserId(9L);
            AlbumInfoVo vo = albumVo("1", null);
            vo.setPayType(SystemConstant.ALBUM_PAY_TYPE_REQUIRE);

            service.saveAlbumInfo(vo);

            ArgumentCaptor<AlbumInfo> captor = ArgumentCaptor.forClass(AlbumInfo.class);
            verify(albumInfoMapper).insert(captor.capture());
            AlbumInfo inserted = captor.getValue();
            assertEquals(9L, inserted.getUserId());
            assertEquals(SystemConstant.ALBUM_STATUS_PASS, inserted.getStatus());
            assertEquals(3, inserted.getTracksForFree());
            // 新专辑初始化 4 条统计记录
            verify(albumStatMapper, times(4)).insert(any(AlbumStat.class));
            verify(rabbitService).sendMessage(MqConst.EXCHANGE_ALBUM, MqConst.ROUTING_ALBUM_UPPER, inserted.getId());
        }

        @Test
        void freeAlbum_keepsNullFreeTracksAndStaysPrivateWithoutMq() {
            AuthContextHolder.setUserId(9L);
            AlbumInfoVo vo = albumVo("0", null);
            vo.setPayType(SystemConstant.ALBUM_PAY_TYPE_FREE);

            service.saveAlbumInfo(vo);

            ArgumentCaptor<AlbumInfo> captor = ArgumentCaptor.forClass(AlbumInfo.class);
            verify(albumInfoMapper).insert(captor.capture());
            assertNull(captor.getValue().getTracksForFree());
            verify(albumStatMapper, times(4)).insert(any(AlbumStat.class));
            verify(rabbitService, never()).sendMessage(anyString(), anyString(), any());
        }

        @Test
        void withAttributes_savesBatchWithAlbumId() {
            AuthContextHolder.setUserId(9L);
            AlbumInfoVo vo = albumVo("0", Collections.singletonList(attributeVo()));
            vo.setPayType(SystemConstant.ALBUM_PAY_TYPE_FREE);
            when(albumInfoMapper.insert(any(AlbumInfo.class))).thenAnswer(invocation -> {
                invocation.getArgument(0, AlbumInfo.class).setId(66L);
                return 1;
            });

            service.saveAlbumInfo(vo);

            ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
            verify(albumAttributeValueService).saveBatch(captor.capture());
            assertEquals(66L, ((List<AlbumAttributeValue>) captor.getValue()).get(0).getAlbumId());
        }
    }
}
