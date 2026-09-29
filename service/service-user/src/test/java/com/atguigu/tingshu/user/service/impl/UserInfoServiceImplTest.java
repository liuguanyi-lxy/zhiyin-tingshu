package com.atguigu.tingshu.user.service.impl;

import com.atguigu.tingshu.account.client.UserAccountFeignClient;
import com.atguigu.tingshu.album.client.TrackInfoFeignClient;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.model.user.UserInfo;
import com.atguigu.tingshu.model.user.UserPaidAlbum;
import com.atguigu.tingshu.model.user.UserPaidTrack;
import com.atguigu.tingshu.model.user.UserVipService;
import com.atguigu.tingshu.model.user.VipServiceConfig;
import com.atguigu.tingshu.user.mapper.UserInfoMapper;
import com.atguigu.tingshu.user.mapper.UserPaidAlbumMapper;
import com.atguigu.tingshu.user.mapper.UserPaidTrackMapper;
import com.atguigu.tingshu.user.mapper.UserVipServiceMapper;
import com.atguigu.tingshu.user.mapper.VipServiceConfigMapper;
import com.atguigu.tingshu.user.strategy.ItemTypeStrategy;
import com.atguigu.tingshu.user.strategy.StrategyContext;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import com.atguigu.tingshu.vo.user.UserPaidRecordVo;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
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
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户核心业务单元测试（纯 Mockito，不依赖数据库/Redis/MQ/远程服务）。
 * 覆盖：登录用户信息、VIP 到期、购买记录（专辑/声音/VIP 三分支）、购买判断、播放相关查询。
 */
@ExtendWith(MockitoExtension.class)
class UserInfoServiceImplTest {

    @Mock
    private UserInfoMapper userInfoMapper;
    @Mock
    private UserPaidAlbumMapper userPaidAlbumMapper;
    @Mock
    private UserPaidTrackMapper userPaidTrackMapper;
    @Mock
    private TrackInfoFeignClient trackInfoFeignClient;
    @Mock
    private VipServiceConfigMapper vipServiceConfigMapper;
    @Mock
    private UserVipServiceMapper userVipServiceMapper;
    @Mock
    private StrategyContext strategyContext;
    @Mock
    private UserAccountFeignClient userAccountFeignClient;
    @InjectMocks
    private UserInfoServiceImpl service;

    @BeforeAll
    static void initTableMeta() {
        // LambdaQueryWrapper 解析 UserPaidAlbum/UserPaidTrack 列名需要实体元数据
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), UserPaidAlbum.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), UserPaidTrack.class);
    }

    @BeforeEach
    void injectBaseMapper() throws Exception {
        // ServiceImpl 继承的 baseMapper 与显式声明的 userInfoMapper 同类型，统一注入同一个 mock
        Field baseMapper = com.baomidou.mybatisplus.extension.service.impl.ServiceImpl.class.getDeclaredField("baseMapper");
        baseMapper.setAccessible(true);
        baseMapper.set(service, userInfoMapper);
    }

    private UserPaidRecordVo recordVo(String itemType, List<Long> itemIds) {
        UserPaidRecordVo vo = new UserPaidRecordVo();
        vo.setOrderNo("ORDER123");
        vo.setUserId(9L);
        vo.setItemType(itemType);
        vo.setItemIdList(itemIds);
        return vo;
    }

    @Nested
    class UserAndAccount {

        @Test
        void saveUserAndInitAccount_insertsUserAndCallsFeign() {
            UserInfo userInfo = new UserInfo();
            userInfo.setPhone("13800000000");

            service.saveUserAndInitAccount(userInfo);

            verify(userInfoMapper).insert(userInfo);
            verify(userAccountFeignClient).addUserAccount(userInfo.getId());
        }

        @Test
        void updateVipExpireStatus_delegatesToMapper() {
            service.updateVipExpireStatus();
            verify(userInfoMapper).updateIsVip();
        }

        @Test
        void getUserInfoVoByUserId_found_copiesProperties() {
            UserInfo userInfo = new UserInfo();
            userInfo.setId(9L);
            userInfo.setNickname("小明");
            userInfo.setIsVip(1);
            when(userInfoMapper.selectById(9L)).thenReturn(userInfo);

            UserInfoVo vo = service.getUserInfoVoByUserId(9L);

            assertEquals(9L, vo.getId());
            assertEquals("小明", vo.getNickname());
        }

        @Test
        void getUserInfoVoByUserId_missing_throwsLoginAuth() {
            when(userInfoMapper.selectById(404L)).thenReturn(null);

            assertThrows(GuiguException.class, () -> service.getUserInfoVoByUserId(404L));
        }
    }

    @Nested
    class SavePaidRecordDispatch {

        @Test
        void delegatesToResolvedStrategy() {
            UserPaidRecordVo vo = recordVo(SystemConstant.ORDER_ITEM_TYPE_ALBUM, Collections.singletonList(1L));
            ItemTypeStrategy strategy = org.mockito.Mockito.mock(ItemTypeStrategy.class);
            when(strategyContext.getStrategy(SystemConstant.ORDER_ITEM_TYPE_ALBUM)).thenReturn(strategy);

            service.savePaidRecord(vo);

            verify(strategy).savePaidRecord(vo);
        }

        @Test
        void unknownItemType_propagatesException() {
            UserPaidRecordVo vo = recordVo("9999", Collections.singletonList(1L));
            when(strategyContext.getStrategy("9999")).thenThrow(new GuiguException(500, "该策略9999实现类不存在"));

            assertThrows(GuiguException.class, () -> service.savePaidRecord(vo));
        }
    }

    @Nested
    class SavePaidRecord2Album {

        @Test
        void duplicateOrder_skipsInsert() {
            when(userPaidAlbumMapper.selectOne(any())).thenReturn(new UserPaidAlbum());

            service.savePaidRecord2(recordVo(SystemConstant.ORDER_ITEM_TYPE_ALBUM, Collections.singletonList(1L)));

            verify(userPaidAlbumMapper, never()).insert(any(UserPaidAlbum.class));
        }

        @Test
        void firstOrder_insertsWithFirstItemIdAsAlbumId() {
            when(userPaidAlbumMapper.selectOne(any())).thenReturn(null);

            service.savePaidRecord2(recordVo(SystemConstant.ORDER_ITEM_TYPE_ALBUM, Collections.singletonList(66L)));

            ArgumentCaptor<UserPaidAlbum> captor = ArgumentCaptor.forClass(UserPaidAlbum.class);
            verify(userPaidAlbumMapper).insert(captor.capture());
            assertEquals(9L, captor.getValue().getUserId());
            assertEquals("ORDER123", captor.getValue().getOrderNo());
            assertEquals(66L, captor.getValue().getAlbumId());
        }
    }

    @Nested
    class SavePaidRecord2Track {

        @Test
        void duplicateOrder_skipsFeignAndInsert() {
            when(userPaidTrackMapper.selectCount(any())).thenReturn(2L);

            service.savePaidRecord2(recordVo(SystemConstant.ORDER_ITEM_TYPE_TRACK, Arrays.asList(1L, 2L)));

            verify(trackInfoFeignClient, never()).getTrackInfo(anyLong());
            verify(userPaidTrackMapper, never()).insert(any(UserPaidTrack.class));
        }

        @Test
        void firstOrder_insertsEveryTrackWithAlbumIdFromFeign() {
            when(userPaidTrackMapper.selectCount(any())).thenReturn(0L);
            TrackInfo trackInfo = new TrackInfo();
            trackInfo.setAlbumId(66L);
            when(trackInfoFeignClient.getTrackInfo(11L)).thenReturn(Result.ok(trackInfo));

            service.savePaidRecord2(recordVo(SystemConstant.ORDER_ITEM_TYPE_TRACK, Arrays.asList(11L, 12L)));

            ArgumentCaptor<UserPaidTrack> captor = ArgumentCaptor.forClass(UserPaidTrack.class);
            verify(userPaidTrackMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
            List<UserPaidTrack> inserted = captor.getAllValues();
            assertEquals(Arrays.asList(11L, 12L), Arrays.asList(inserted.get(0).getTrackId(), inserted.get(1).getTrackId()));
            assertEquals(66L, inserted.get(0).getAlbumId());
            assertEquals(66L, inserted.get(1).getAlbumId());
        }
    }

    @Nested
    class SavePaidRecord2Vip {

        @Test
        void nonVipUser_expireTimeIsNowPlusMonths() {
            UserInfo userInfo = new UserInfo();
            userInfo.setId(9L);
            userInfo.setIsVip(0);
            when(userInfoMapper.selectById(9L)).thenReturn(userInfo);
            VipServiceConfig config = new VipServiceConfig();
            config.setServiceMonth(3);
            when(vipServiceConfigMapper.selectById(5L)).thenReturn(config);

            long before = System.currentTimeMillis();
            service.savePaidRecord2(recordVo(SystemConstant.ORDER_ITEM_TYPE_VIP, Collections.singletonList(5L)));
            long after = System.currentTimeMillis();

            ArgumentCaptor<UserVipService> captor = ArgumentCaptor.forClass(UserVipService.class);
            verify(userVipServiceMapper).insert(captor.capture());
            UserVipService userVipService = captor.getValue();
            // now + 3 个月，误差 5 秒内
            long expectLow = new org.joda.time.LocalDateTime(new Date(before)).plusMonths(3).toDate().getTime();
            long expectHigh = new org.joda.time.LocalDateTime(new Date(after)).plusMonths(3).toDate().getTime();
            assertTrue(userVipService.getExpireTime().getTime() >= expectLow - 5000
                    && userVipService.getExpireTime().getTime() <= expectHigh + 5000);
            assertEquals("ORDER123", userVipService.getOrderNo());

            ArgumentCaptor<UserInfo> userCaptor = ArgumentCaptor.forClass(UserInfo.class);
            verify(userInfoMapper).updateById(userCaptor.capture());
            assertEquals(1, userCaptor.getValue().getIsVip());
            assertEquals(userVipService.getExpireTime(), userCaptor.getValue().getVipExpireTime());
        }

        @Test
        void unexpiredVipUser_renewsFromOldExpireTime() {
            // VIP 且未过期：以购买前到期时间为基准续期
            Date oldExpire = new Date(System.currentTimeMillis() + 10L * 24 * 60 * 60 * 1000);
            UserInfo userInfo = new UserInfo();
            userInfo.setId(9L);
            userInfo.setIsVip(1);
            userInfo.setVipExpireTime(oldExpire);
            when(userInfoMapper.selectById(9L)).thenReturn(userInfo);
            VipServiceConfig config = new VipServiceConfig();
            config.setServiceMonth(1);
            when(vipServiceConfigMapper.selectById(5L)).thenReturn(config);

            service.savePaidRecord2(recordVo(SystemConstant.ORDER_ITEM_TYPE_VIP, Collections.singletonList(5L)));

            ArgumentCaptor<UserVipService> captor = ArgumentCaptor.forClass(UserVipService.class);
            verify(userVipServiceMapper).insert(captor.capture());
            Date expect = new org.joda.time.LocalDateTime(oldExpire).plusMonths(1).toDate();
            assertEquals(expect, captor.getValue().getExpireTime());
        }
    }

    @Nested
    class PurchaseQuery {

        @Test
        void findPaidUserTrackList_mapsTrackIds() {
            UserPaidTrack paid = new UserPaidTrack();
            paid.setTrackId(101L);
            UserPaidTrack paid2 = new UserPaidTrack();
            paid2.setTrackId(102L);
            when(userPaidTrackMapper.selectList(any())).thenReturn(Arrays.asList(paid, paid2));

            List<Long> result = service.findPaidUserTrackList(9L, 66L);

            assertEquals(Arrays.asList(101L, 102L), result);
        }

        @Test
        void findPaidUserTrackList_empty_returnsEmptyList() {
            when(userPaidTrackMapper.selectList(any())).thenReturn(Collections.emptyList());

            assertTrue(service.findPaidUserTrackList(9L, 66L).isEmpty());
        }

        @Test
        void isPaidAlbum_trueWhenRecordExists() {
            when(userPaidAlbumMapper.exists(any())).thenReturn(true);
            assertTrue(service.isPaidAlbum(66L, 9L));
        }

        @Test
        void isPaidAlbum_falseWhenNoRecord() {
            when(userPaidAlbumMapper.exists(any())).thenReturn(false);
            assertEquals(Boolean.FALSE, service.isPaidAlbum(66L, 9L));
        }

        @Test
        void userIsPaidTrack_albumPurchased_allTracksFree() {
            when(userPaidAlbumMapper.selectOne(any())).thenReturn(new UserPaidAlbum());

            var map = service.userIsPaidTrack(9L, 66L, Arrays.asList(1L, 2L));

            assertEquals(SystemConstant.TRACK_IS_BUY, map.get(1L));
            assertEquals(SystemConstant.TRACK_IS_BUY, map.get(2L));
            verify(userPaidTrackMapper, never()).selectList(any());
        }

        @Test
        void userIsPaidTrack_partialPurchased_mixedResult() {
            when(userPaidAlbumMapper.selectOne(any())).thenReturn(null);
            UserPaidTrack paid = new UserPaidTrack();
            paid.setTrackId(2L);
            when(userPaidTrackMapper.selectList(any())).thenReturn(Collections.singletonList(paid));

            var map = service.userIsPaidTrack(9L, 66L, Arrays.asList(1L, 2L));

            assertEquals(SystemConstant.TRACK_NOT_BUY, map.get(1L));
            assertEquals(SystemConstant.TRACK_IS_BUY, map.get(2L));
        }

        @Test
        void userIsPaidTrack_nonePurchased_allNotBuy() {
            when(userPaidAlbumMapper.selectOne(any())).thenReturn(null);
            when(userPaidTrackMapper.selectList(any())).thenReturn(Collections.emptyList());

            var map = service.userIsPaidTrack(9L, 66L, Arrays.asList(1L, 2L));

            assertEquals(SystemConstant.TRACK_NOT_BUY, map.get(1L));
            assertEquals(SystemConstant.TRACK_NOT_BUY, map.get(2L));
        }
    }

    /**【回归锁】CDC 对齐契约：UserCdcHandler 删除的 key 必须与 @RedisCache 生成格式一致 */
    @Test
    void getUserInfoVoByUserId_cachePrefix_staysAlignedWithCdcHandler() throws Exception {
        java.lang.reflect.Method method = UserInfoServiceImpl.class.getMethod("getUserInfoVoByUserId", Long.class);
        com.atguigu.tingshu.common.RedisCache.annotation.RedisCache redisCache =
                method.getAnnotation(com.atguigu.tingshu.common.RedisCache.annotation.RedisCache.class);

        org.junit.jupiter.api.Assertions.assertNotNull(redisCache, "getUserInfoVoByUserId 必须带 @RedisCache，否则 user_info 的 CDC 缓存失效链路失效");
        org.junit.jupiter.api.Assertions.assertEquals("userInfoVo:", redisCache.prefix(),
                "prefix 变更会破坏 UserCdcHandler 的 key 对齐（CDC 删除 userInfoVo:[id]）");
    }
}
