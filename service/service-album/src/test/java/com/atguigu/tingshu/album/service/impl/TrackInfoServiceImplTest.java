package com.atguigu.tingshu.album.service.impl;

import com.atguigu.tingshu.album.mapper.AlbumInfoMapper;
import com.atguigu.tingshu.album.mapper.TrackInfoMapper;
import com.atguigu.tingshu.album.mapper.TrackStatMapper;
import com.atguigu.tingshu.album.service.AlbumInfoService;
import com.atguigu.tingshu.album.service.VodService;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.model.album.TrackStat;
import com.atguigu.tingshu.user.client.UserInfoFeignClient;
import com.atguigu.tingshu.vo.album.AlbumTrackListVo;
import com.atguigu.tingshu.vo.album.TrackInfoVo;
import com.atguigu.tingshu.vo.album.TrackMediaInfoVo;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.IPage;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 声音业务单元测试（纯 Mockito，不连数据库/腾讯云 VOD/用户微服务）。
 * 覆盖：下单声音列表、购买套餐边界（0/5/10/15/20/30/45/50+）、专辑声音列表付费标识、
 * 声音更新（VOD 分支）、声音删除、声音保存（排序值/统计初始化）。
 */
@ExtendWith(MockitoExtension.class)
class TrackInfoServiceImplTest {

    @Mock
    private TrackInfoMapper trackInfoMapper;
    @Mock
    private VodService vodService;
    @Mock
    private AlbumInfoMapper albumInfoMapper;
    @Mock
    private TrackStatMapper trackStatMapper;
    @Mock
    private UserInfoFeignClient userInfoFeignClient;
    @Mock
    private AlbumInfoService albumInfoService;
    @InjectMocks
    private TrackInfoServiceImpl service;

    @BeforeAll
    static void initTableMeta() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), TrackInfo.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), com.atguigu.tingshu.model.album.TrackStat.class);
    }

    @BeforeEach
    void injectBaseMapper() throws Exception {
        Field baseMapper = com.baomidou.mybatisplus.extension.service.impl.ServiceImpl.class.getDeclaredField("baseMapper");
        baseMapper.setAccessible(true);
        baseMapper.set(service, trackInfoMapper);
    }

    @AfterEach
    void clearAuthContext() {
        AuthContextHolder.removeUserId();
    }

    private TrackInfo track(Long albumId, int orderNum, String mediaFileId) {
        TrackInfo trackInfo = new TrackInfo();
        trackInfo.setId(100L);
        trackInfo.setAlbumId(albumId);
        trackInfo.setOrderNum(orderNum);
        trackInfo.setMediaFileId(mediaFileId);
        return trackInfo;
    }

    private AlbumInfo paidAlbum() {
        AlbumInfo albumInfo = new AlbumInfo();
        albumInfo.setId(66L);
        albumInfo.setPayType(SystemConstant.ALBUM_PAY_TYPE_REQUIRE);
        albumInfo.setTracksForFree(3);
        albumInfo.setPrice(new BigDecimal("1.50"));
        albumInfo.setIncludeTrackCount(5);
        return albumInfo;
    }

    private List<TrackInfo> tracksAfter(int count) {
        List<TrackInfo> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            TrackInfo t = new TrackInfo();
            t.setId((long) i);
            t.setAlbumId(66L);
            list.add(t);
        }
        return list;
    }

    @Nested
    class FindPaidTrackInfoList {

        @Test
        void trackCountZero_returnsOnlySelectedTrack() {
            when(trackInfoMapper.selectById(100L)).thenReturn(track(66L, 10, "m1"));

            List<TrackInfo> result = service.findPaidTrackInfoList(100L, 0);

            assertEquals(1, result.size());
            assertEquals(100L, result.get(0).getId());
            verify(userInfoFeignClient, never()).findUserIsPaidTrack(anyLong());
        }

        @Test
        void trackCountPositive_queriesTracksAfterCurrent() {
            when(trackInfoMapper.selectById(100L)).thenReturn(track(66L, 10, "m1"));
            when(userInfoFeignClient.findUserIsPaidTrack(66L)).thenReturn(Result.ok(Collections.emptyList()));
            when(trackInfoMapper.selectList(any())).thenReturn(tracksAfter(2));

            List<TrackInfo> result = service.findPaidTrackInfoList(100L, 5);

            assertEquals(2, result.size());
        }
    }

    @Nested
    class FindUserTrackPaidListBoundaries {

        private List<Map<String, Object>> plansFor(int remainingAfterCurrent) {
            when(trackInfoMapper.selectById(100L)).thenReturn(track(66L, 10, "m1"));
            when(albumInfoMapper.selectById(66L)).thenReturn(paidAlbum());
            when(userInfoFeignClient.findUserIsPaidTrack(66L)).thenReturn(Result.ok(Collections.emptyList()));
            when(trackInfoMapper.selectList(any())).thenReturn(tracksAfter(remainingAfterCurrent));
            return service.findUserTrackPaidList(100L);
        }

        private Map<String, Object> plan(List<Map<String, Object>> plans, String name) {
            return plans.stream().filter(p -> name.equals(p.get("name"))).findFirst().orElse(null);
        }

        @Test
        void zeroRemaining_onlyCurrentTrackPlan() {
            List<Map<String, Object>> plans = plansFor(0);

            assertEquals(1, plans.size());
            assertEquals("本集", plans.get(0).get("name"));
            assertEquals(0, plans.get(0).get("trackCount"));
        }

        @Test
        void fiveRemaining_addsSingleTailPlan() {
            List<Map<String, Object>> plans = plansFor(5);

            assertEquals(2, plans.size());
            Map<String, Object> tail = plan(plans, "后5集");
            assertEquals(5, tail.get("trackCount"));
            assertEquals(0, new BigDecimal("1.50").multiply(new BigDecimal(5)).compareTo((BigDecimal) tail.get("price")));
        }

        @Test
        void tenRemaining_addsFixedTenPlan() {
            List<Map<String, Object>> plans = plansFor(10);

            assertEquals(2, plans.size());
            Map<String, Object> tail = plan(plans, "后10集");
            assertEquals(10, tail.get("trackCount"));
            assertEquals(0, new BigDecimal("15.00").compareTo((BigDecimal) tail.get("price")));
        }

        @Test
        void fifteenRemaining_addsTenAndFifteenPlans() {
            List<Map<String, Object>> plans = plansFor(15);

            assertEquals(3, plans.size());
            assertEquals(10, plan(plans, "后10集").get("trackCount"));
            Map<String, Object> tail = plan(plans, "后15集");
            assertEquals(15, tail.get("trackCount"));
            assertEquals(0, new BigDecimal("22.50").compareTo((BigDecimal) tail.get("price")));
        }

        @Test
        void twentyRemaining_addsFixedTwentyPlan() {
            List<Map<String, Object>> plans = plansFor(20);

            assertEquals(3, plans.size());
            assertEquals(20, plan(plans, "后20集").get("trackCount"));
        }

        @Test
        void thirtyRemaining_addsFixedThirtyPlan() {
            List<Map<String, Object>> plans = plansFor(30);

            // 本集 + 后10集 + 后20集 + 后30集
            assertEquals(4, plans.size());
            assertEquals(30, plan(plans, "后30集").get("trackCount"));
        }

        @Test
        void fortyFiveRemaining_addsFortyAndFortyFivePlans() {
            List<Map<String, Object>> plans = plansFor(45);

            // 本集 + 后10集 + 后20集 + 后30集 + 后40集 + 后45集
            assertEquals(6, plans.size());
            assertEquals(40, plan(plans, "后40集").get("trackCount"));
            assertEquals(45, plan(plans, "后45集").get("trackCount"));
        }

        @Test
        void fiftyRemaining_capsAtFifty() {
            List<Map<String, Object>> plans = plansFor(50);

            Map<String, Object> tail = plan(plans, "后50集");
            assertEquals(50, tail.get("trackCount"));
            assertEquals(0, new BigDecimal("75.00").compareTo((BigDecimal) tail.get("price")));
        }

        @Test
        void excludesAlreadyPaidTracksFromPlans() {
            when(trackInfoMapper.selectById(100L)).thenReturn(track(66L, 10, "m1"));
            when(albumInfoMapper.selectById(66L)).thenReturn(paidAlbum());
            when(userInfoFeignClient.findUserIsPaidTrack(66L)).thenReturn(Result.ok(Arrays.asList(1L, 2L, 3L)));
            when(trackInfoMapper.selectList(any())).thenReturn(tracksAfter(5));

            List<Map<String, Object>> plans = service.findUserTrackPaidList(100L);

            // 5 集中已购 3 集，只剩 2 集
            assertEquals(2, plan(plans, "后2集").get("trackCount"));
        }
    }

    @Nested
    class FindAlbumTrackPage {

        private IPage<AlbumTrackListVo> pageWithTracks(Integer... orderNums) {
            Page<AlbumTrackListVo> page = new Page<>(1, 10);
            List<AlbumTrackListVo> records = new ArrayList<>();
            long id = 1;
            for (Integer orderNum : orderNums) {
                AlbumTrackListVo vo = new AlbumTrackListVo();
                vo.setTrackId(id++);
                vo.setOrderNum(orderNum);
                records.add(vo);
            }
            page.setRecords(records);
            return page;
        }

        private void mockPageAndAlbum(String payType, Integer freeNum) {
            IPage<AlbumTrackListVo> page = pageWithTracks(1, 2, 3, 4);
            when(trackInfoMapper.findAlbumTrackPage(any(), eq(66L))).thenReturn(page);
            AlbumInfo albumInfo = paidAlbum();
            albumInfo.setPayType(payType);
            albumInfo.setTracksForFree(freeNum);
            when(albumInfoMapper.selectById(66L)).thenReturn(albumInfo);
        }

        @Test
        void anonymousOnPaidAlbum_marksTracksBeyondFreeCount() {
            mockPageAndAlbum(SystemConstant.ALBUM_PAY_TYPE_REQUIRE, 3);

            IPage<AlbumTrackListVo> result = service.findAlbumTrackPage(new Page<>(1, 10), 66L, null);

            for (AlbumTrackListVo vo : result.getRecords()) {
                assertEquals(vo.getOrderNum() > 3, Boolean.TRUE.equals(vo.getIsShowPaidMark()));
            }
        }

        @Test
        void anonymousOnFreeAlbum_noPaidMark() {
            mockPageAndAlbum(SystemConstant.ALBUM_PAY_TYPE_FREE, 3);

            IPage<AlbumTrackListVo> result = service.findAlbumTrackPage(new Page<>(1, 10), 66L, null);

            result.getRecords().forEach(vo -> assertFalse(Boolean.TRUE.equals(vo.getIsShowPaidMark())));
        }

        @Test
        void vipFreeAlbumNonVipUser_marksUnpaidTracks() {
            mockPageAndAlbum(SystemConstant.ALBUM_PAY_TYPE_VIPFREE, 3);
            UserInfoVo userInfoVo = new UserInfoVo();
            userInfoVo.setIsVip(SystemConstant.USER_NOT_VIP);
            when(userInfoFeignClient.getUserInfoVo(9L)).thenReturn(Result.ok(userInfoVo));
            Map<Long, Integer> paidMap = new HashMap<>();
            paidMap.put(4L, SystemConstant.TRACK_IS_BUY);
            when(userInfoFeignClient.userIsPaidTrack(eq(9L), any())).thenReturn(Result.ok(paidMap));

            IPage<AlbumTrackListVo> result = service.findAlbumTrackPage(new Page<>(1, 10), 66L, 9L);

            for (AlbumTrackListVo vo : result.getRecords()) {
                boolean expectMark = vo.getOrderNum() > 3 && vo.getTrackId() != 4L;
                assertEquals(expectMark, Boolean.TRUE.equals(vo.getIsShowPaidMark()));
            }
        }

        @Test
        void vipFreeAlbumValidVipUser_noPaidMark() {
            mockPageAndAlbum(SystemConstant.ALBUM_PAY_TYPE_VIPFREE, 3);
            UserInfoVo userInfoVo = new UserInfoVo();
            userInfoVo.setIsVip(SystemConstant.USER_IS_VIP);
            userInfoVo.setVipExpireTime(new java.util.Date(System.currentTimeMillis() + 86_400_000L));
            when(userInfoFeignClient.getUserInfoVo(9L)).thenReturn(Result.ok(userInfoVo));

            IPage<AlbumTrackListVo> result = service.findAlbumTrackPage(new Page<>(1, 10), 66L, 9L);

            result.getRecords().forEach(vo -> assertFalse(Boolean.TRUE.equals(vo.getIsShowPaidMark())));
            verify(userInfoFeignClient, never()).userIsPaidTrack(anyLong(), any());
        }

        @Test
        void vipFreeAlbumMissingUser_throws() {
            mockPageAndAlbum(SystemConstant.ALBUM_PAY_TYPE_VIPFREE, 3);
            when(userInfoFeignClient.getUserInfoVo(9L)).thenReturn(Result.ok(null));

            assertThrows(IllegalArgumentException.class,
                    () -> service.findAlbumTrackPage(new Page<>(1, 10), 66L, 9L));
        }

        @Test
        void requireAlbumPurchasedAllTracks_noPaidMark() {
            mockPageAndAlbum(SystemConstant.ALBUM_PAY_TYPE_REQUIRE, 3);
            Map<Long, Integer> paidMap = new HashMap<>();
            paidMap.put(1L, SystemConstant.TRACK_IS_BUY);
            paidMap.put(2L, SystemConstant.TRACK_IS_BUY);
            paidMap.put(3L, SystemConstant.TRACK_IS_BUY);
            paidMap.put(4L, SystemConstant.TRACK_IS_BUY);
            when(userInfoFeignClient.userIsPaidTrack(eq(9L), any())).thenReturn(Result.ok(paidMap));

            IPage<AlbumTrackListVo> result = service.findAlbumTrackPage(new Page<>(1, 10), 66L, 9L);

            result.getRecords().forEach(vo -> assertFalse(Boolean.TRUE.equals(vo.getIsShowPaidMark())));
        }
    }

    @Nested
    class UpdateTrack {

        @Test
        void sameMediaFile_skipsVodAndUpdatesDirectly() {
            when(trackInfoMapper.selectById(100L)).thenReturn(track(66L, 10, "m1"));
            TrackInfoVo vo = new TrackInfoVo();
            vo.setMediaFileId("m1");
            vo.setTrackTitle("改名");

            service.updateTrackInfo(100L, vo);

            verify(vodService, never()).removeTrackMedia(anyString());
            verify(vodService, never()).getTrackMediaInfo(anyString());
            ArgumentCaptor<TrackInfo> captor = ArgumentCaptor.forClass(TrackInfo.class);
            verify(trackInfoMapper).updateById(captor.capture());
            assertEquals("改名", captor.getValue().getTrackTitle());
        }

        @Test
        void changedMediaFile_missingMediaInfo_throwsWithoutUpdate() {
            when(trackInfoMapper.selectById(100L)).thenReturn(track(66L, 10, "m1"));
            when(vodService.getTrackMediaInfo("m2")).thenReturn(null);
            TrackInfoVo vo = new TrackInfoVo();
            vo.setMediaFileId("m2");

            assertThrows(RuntimeException.class, () -> service.updateTrackInfo(100L, vo));

            verify(vodService).removeTrackMedia("m1");
            verify(trackInfoMapper, never()).updateById(any(TrackInfo.class));
        }

        @Test
        void changedMediaFile_copiesNewMediaProperties() {
            when(trackInfoMapper.selectById(100L)).thenReturn(track(66L, 10, "m1"));
            TrackMediaInfoVo mediaInfo = new TrackMediaInfoVo();
            mediaInfo.setDuration(60f);
            mediaInfo.setSize(1024L);
            mediaInfo.setType("audio/mpeg");
            mediaInfo.setMediaUrl("http://media/m2");
            when(vodService.getTrackMediaInfo("m2")).thenReturn(mediaInfo);
            TrackInfoVo vo = new TrackInfoVo();
            vo.setMediaFileId("m2");
            vo.setTrackTitle("替换声音");

            service.updateTrackInfo(100L, vo);

            ArgumentCaptor<TrackInfo> captor = ArgumentCaptor.forClass(TrackInfo.class);
            verify(trackInfoMapper).updateById(captor.capture());
            assertEquals(60f, captor.getValue().getMediaDuration());
            assertEquals(1024L, captor.getValue().getMediaSize());
            assertEquals("audio/mpeg", captor.getValue().getMediaType());
            assertEquals("http://media/m2", captor.getValue().getMediaUrl());
        }
    }

    @Nested
    class RemoveAndSaveTrack {

        @Test
        void removeTrackInfo_decrementsAlbumAndCleansAll() {
            when(trackInfoMapper.selectById(100L)).thenReturn(track(66L, 10, "m1"));
            when(trackInfoMapper.deleteById(100L)).thenReturn(1);
            when(albumInfoMapper.selectById(66L)).thenReturn(paidAlbum());

            service.removeTrackInfo(100L);

            verify(trackInfoMapper).deleteById(100L);
            ArgumentCaptor<AlbumInfo> albumCaptor = ArgumentCaptor.forClass(AlbumInfo.class);
            verify(albumInfoMapper).updateById(albumCaptor.capture());
            assertEquals(4, albumCaptor.getValue().getIncludeTrackCount());
            verify(trackStatMapper).delete(any());
            verify(vodService).removeTrackMedia("m1");
        }

        @Test
        void saveTrackInfo_emptyAlbum_orderNumStartsAtOne() {
            AuthContextHolder.setUserId(9L);
            when(trackInfoMapper.selectMaxOrderNUm(66L)).thenReturn(null);
            TrackMediaInfoVo mediaInfo = new TrackMediaInfoVo();
            mediaInfo.setDuration(30f);
            mediaInfo.setSize(512L);
            mediaInfo.setType("audio/mpeg");
            mediaInfo.setMediaUrl("http://media/m3");
            when(vodService.getTrackMediaInfo("m3")).thenReturn(mediaInfo);
            when(albumInfoMapper.selectById(66L)).thenReturn(paidAlbum());
            TrackInfoVo vo = new TrackInfoVo();
            vo.setAlbumId(66L);
            vo.setMediaFileId("m3");
            vo.setTrackTitle("新声音");

            service.saveTrackInfo(vo);

            ArgumentCaptor<TrackInfo> captor = ArgumentCaptor.forClass(TrackInfo.class);
            verify(trackInfoMapper).insert(captor.capture());
            assertEquals(1, captor.getValue().getOrderNum());
            assertEquals(9L, captor.getValue().getUserId());
            // 4 条统计初始化
            verify(trackStatMapper, times(4)).insert(any(TrackStat.class));
        }

        @Test
        void saveTrackInfo_existingMaxOrder_appendsAfterMax() {
            AuthContextHolder.setUserId(9L);
            when(trackInfoMapper.selectMaxOrderNUm(66L)).thenReturn(5);
            when(vodService.getTrackMediaInfo("m3")).thenReturn(new TrackMediaInfoVo());
            when(albumInfoMapper.selectById(66L)).thenReturn(paidAlbum());
            TrackInfoVo vo = new TrackInfoVo();
            vo.setAlbumId(66L);
            vo.setMediaFileId("m3");

            service.saveTrackInfo(vo);

            ArgumentCaptor<TrackInfo> captor = ArgumentCaptor.forClass(TrackInfo.class);
            verify(trackInfoMapper).insert(captor.capture());
            assertEquals(6, captor.getValue().getOrderNum());
            ArgumentCaptor<AlbumInfo> albumCaptor = ArgumentCaptor.forClass(AlbumInfo.class);
            verify(albumInfoMapper).updateById(albumCaptor.capture());
            assertEquals(6, albumCaptor.getValue().getIncludeTrackCount());
        }

        @Test
        void findUserTrackPage_delegatesToMapper() {
            Page<com.atguigu.tingshu.vo.album.TrackListVo> pageParam = new Page<>(1, 10);
            com.atguigu.tingshu.query.album.TrackInfoQuery query = new com.atguigu.tingshu.query.album.TrackInfoQuery();
            when(trackInfoMapper.selectUserTrackPage(pageParam, query)).thenReturn(pageParam);

            assertEquals(pageParam, service.findUserTrackPage(pageParam, query));
        }
    }

    @Nested
    class GetTrackStatVo {

        private TrackStat stat(String statType, int num) {
            // TrackStat 只有三参构造器（无无参构造）
            return new TrackStat(100L, statType, num);
        }

        @Test
        void mapsFourStatTypesToVoFields() {
            when(trackStatMapper.selectList(any())).thenReturn(List.of(
                    stat(SystemConstant.TRACK_STAT_PLAY, 101),
                    stat(SystemConstant.TRACK_STAT_COLLECT, 202),
                    stat(SystemConstant.TRACK_STAT_PRAISE, 303),
                    stat(SystemConstant.TRACK_STAT_COMMENT, 404)));

            com.atguigu.tingshu.vo.album.TrackStatVo vo = service.getTrackStatVo(100L);

            assertEquals(101, vo.getPlayStatNum());
            assertEquals(202, vo.getCollectStatNum());
            assertEquals(303, vo.getPraiseStatNum());
            assertEquals(404, vo.getCommentStatNum());
        }

        @Test
        void missingStats_fieldsStayNull() {
            when(trackStatMapper.selectList(any())).thenReturn(Collections.emptyList());

            com.atguigu.tingshu.vo.album.TrackStatVo vo = service.getTrackStatVo(999L);

            assertEquals(null, vo.getPlayStatNum());
            assertEquals(null, vo.getCommentStatNum());
        }
    }
}
