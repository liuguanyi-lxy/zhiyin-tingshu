package com.atguigu.tingshu.user.strategy.impl;

import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.model.user.UserPaidAlbum;
import com.atguigu.tingshu.user.mapper.UserPaidAlbumMapper;
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

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 购买专辑策略单元测试：防重复插入 + 首次购买按 itemIdList[0] 落库。
 */
@ExtendWith(MockitoExtension.class)
class AlbumItemTypeStrategyImplTest {

    @Mock
    private UserPaidAlbumMapper userPaidAlbumMapper;
    @InjectMocks
    private AlbumItemTypeStrategyImpl strategy;

    @BeforeAll
    static void initTableMeta() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), UserPaidAlbum.class);
    }

    private UserPaidRecordVo recordVo() {
        UserPaidRecordVo vo = new UserPaidRecordVo();
        vo.setOrderNo("ORDER123");
        vo.setUserId(9L);
        vo.setItemType(SystemConstant.ORDER_ITEM_TYPE_ALBUM);
        vo.setItemIdList(Collections.singletonList(66L));
        return vo;
    }

    @Test
    void duplicateOrder_skipsInsert() {
        when(userPaidAlbumMapper.selectOne(any())).thenReturn(new UserPaidAlbum());

        strategy.savePaidRecord(recordVo());

        verify(userPaidAlbumMapper, never()).insert(any(UserPaidAlbum.class));
    }

    @Test
    void firstOrder_insertsWithAlbumId() {
        when(userPaidAlbumMapper.selectOne(any())).thenReturn(null);

        strategy.savePaidRecord(recordVo());

        ArgumentCaptor<UserPaidAlbum> captor = ArgumentCaptor.forClass(UserPaidAlbum.class);
        verify(userPaidAlbumMapper).insert(captor.capture());
        assertEquals(9L, captor.getValue().getUserId());
        assertEquals("ORDER123", captor.getValue().getOrderNo());
        assertEquals(66L, captor.getValue().getAlbumId());
    }
}
