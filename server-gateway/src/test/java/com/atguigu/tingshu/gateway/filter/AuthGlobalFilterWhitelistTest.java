package com.atguigu.tingshu.gateway.filter;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 网关白名单回归测试（纯反射调用，无容器依赖）。
 * 锁定【修复#4】的安全边界：文件上传与支付轮询必须登录、VIP 配置只放行精确路径、
 * 专辑详情正则只放行数字 id。
 */
class AuthGlobalFilterWhitelistTest {

    private final AuthGlobalFilter filter = new AuthGlobalFilter();

    private boolean whitelisted(String path) {
        try {
            Method method = AuthGlobalFilter.class.getDeclaredMethod("isWhiteList", String.class);
            method.setAccessible(true);
            return (Boolean) method.invoke(filter, path);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void wechatPayNotify_isWhitelisted() {
        assertTrue(whitelisted("/api/payment/wxPay/notify"));
    }

    @Test
    void fileUpload_requiresLogin() {
        // 回归【修复#4】：匿名可向 MinIO 任意传文件的洞已关闭
        assertFalse(whitelisted("/api/album/fileUpload"));
    }

    @Test
    void payStatusPolling_requiresLogin() {
        // 回归【修复#4】：会触发改库发 MQ 的写操作不应匿名放行
        assertFalse(whitelisted("/api/payment/wxPay/queryPayStatus/ORDER123"));
    }

    @Test
    void vipConfigQuery_exactPathOnly() {
        assertTrue(whitelisted("/api/user/vipServiceConfig/findAll"));
        // 收紧后不再放行 findAll 之外的路径（防新增管理端点被连带放行）
        assertFalse(whitelisted("/api/user/vipServiceConfig/findAll/extra"));
        assertFalse(whitelisted("/api/user/vipServiceConfig/update"));
    }

    @Test
    void albumDetail_numericIdOnly() {
        assertTrue(whitelisted("/api/search/albumInfo/123"));
        // 单词路径（如 batchUpperAlbum）不得被详情放行误伤成白名单
        assertFalse(whitelisted("/api/search/albumInfo/batchUpperAlbum"));
    }

    @Test
    void login_isWhitelisted() {
        assertTrue(whitelisted("/api/user/wxLogin/wxLogin/abc123"));
    }

    @Test
    void commentBrowse_whitelistedButWritesRequireLogin() {
        // 评论浏览匿名可看（与专辑声音列表同级）
        assertTrue(whitelisted("/api/comment/findCommentPage/1/0/1/10"));
        // 发布/点赞/删除必须登录
        assertFalse(whitelisted("/api/comment/save"));
        assertFalse(whitelisted("/api/comment/praise/1/abc"));
        assertFalse(whitelisted("/api/comment/remove/1/abc"));
    }

    @Test
    void subscribeCollectQuery_whitelistedButToggleAndListsRequireLogin() {
        // 详情页/播放页匿名也能查询订阅/收藏状态（后端未登录一律返回 false）
        assertTrue(whitelisted("/api/user/userInfo/isSubscribe/123"));
        assertTrue(whitelisted("/api/user/userInfo/isCollect/456"));
        // 切换订阅/收藏、订阅/收藏分页列表必须登录
        assertFalse(whitelisted("/api/user/userInfo/subscribe/123"));
        assertFalse(whitelisted("/api/user/userInfo/collect/456"));
        assertFalse(whitelisted("/api/user/userInfo/findUserSubscribePage/1/10"));
        assertFalse(whitelisted("/api/user/userInfo/findUserCollectPage/1/10"));
    }

    @Test
    void trackStatVo_isWhitelistedForAnonymousPlayer() {
        // 播放页匿名可看声音统计数据（评论/收藏/播放数来源）
        assertTrue(whitelisted("/api/album/trackInfo/getTrackStatVo/123"));
    }
}
