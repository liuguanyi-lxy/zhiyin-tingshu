package com.atguigu.tingshu.search.service.impl;

import com.atguigu.tingshu.album.client.AlbumInfoFeignClient;
import com.atguigu.tingshu.album.client.CategoryFeignClient;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.model.album.BaseCategoryView;
import com.atguigu.tingshu.user.client.UserInfoFeignClient;
import com.atguigu.tingshu.vo.album.AlbumStatVo;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * 专辑详情聚合单元测试（纯 Mockito）。
 * 覆盖【修复#2】对应场景：四个异步 Feign 聚合后结果完整；上游数据缺失时报错。
 */
@ExtendWith(MockitoExtension.class)
class ItemServiceImplTest {

    @Mock
    private AlbumInfoFeignClient albumInfoFeignClient;
    @Mock
    private CategoryFeignClient categoryFeignClient;
    @Mock
    private UserInfoFeignClient userInfoFeignClient;
    @InjectMocks
    private ItemServiceImpl service;

    private AlbumInfo albumInfo() {
        AlbumInfo albumInfo = new AlbumInfo();
        albumInfo.setId(1L);
        albumInfo.setCategory3Id(100L);
        albumInfo.setUserId(8L);
        return albumInfo;
    }

    @Test
    void getItem_aggregatesAllFourParts() {
        when(albumInfoFeignClient.getAlbumInfo(1L)).thenReturn(Result.ok(albumInfo()));
        when(categoryFeignClient.getCategoryView(100L)).thenReturn(Result.ok(new BaseCategoryView()));
        when(userInfoFeignClient.getUserInfoVo(8L)).thenReturn(Result.ok(new UserInfoVo()));
        when(albumInfoFeignClient.getAlbumInfoStat(1L)).thenReturn(Result.ok(new AlbumStatVo()));

        Map<String, Object> result = service.getItem(1L);

        assertNotNull(result.get("albumInfo"));
        assertNotNull(result.get("baseCategoryView"));
        assertNotNull(result.get("announcer"));
        assertNotNull(result.get("albumStatVo"));
        assertEquals(4, result.size());
    }

    @Test
    void getItem_missingAlbum_throws() {
        when(albumInfoFeignClient.getAlbumInfo(anyLong())).thenReturn(Result.ok(null));

        assertThrows(Exception.class, () -> service.getItem(1L));
    }
}
