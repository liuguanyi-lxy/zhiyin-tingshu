package com.atguigu.tingshu.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 用户认证上下文（ThreadLocal）单元测试：set/get/remove 与未设置时返回 null。
 */
class AuthContextHolderTest {

    @Test
    void userId_roundTrip() {
        assertNull(AuthContextHolder.getUserId());

        AuthContextHolder.setUserId(9L);
        assertEquals(9L, AuthContextHolder.getUserId());

        AuthContextHolder.removeUserId();
        assertNull(AuthContextHolder.getUserId());
    }

    @Test
    void username_roundTrip() {
        assertNull(AuthContextHolder.getUsername());

        AuthContextHolder.setUsername("小明");
        assertEquals("小明", AuthContextHolder.getUsername());

        AuthContextHolder.removeUsername();
        assertNull(AuthContextHolder.getUsername());
    }
}
