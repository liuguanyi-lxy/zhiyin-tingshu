package com.atguigu.tingshu.order.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderSignKeyConfigTest {
    private static final String VALID_KEY = "test-only-order-signing-key-at-least-32-characters";

    @Test
    void missingSigningKeyFailsStartup() {
        OrderSignKeyConfig config = new OrderSignKeyConfig();
        ReflectionTestUtils.setField(config, "signKey", "");

        assertThrows(IllegalStateException.class, config::initSignKey);
    }

    @Test
    void shortSigningKeyFailsStartup() {
        OrderSignKeyConfig config = new OrderSignKeyConfig();
        ReflectionTestUtils.setField(config, "signKey", "weak-key");

        assertThrows(IllegalStateException.class, config::initSignKey);
    }

    @Test
    void strongSigningKeyIsLoaded() {
        OrderSignKeyConfig config = new OrderSignKeyConfig();
        ReflectionTestUtils.setField(config, "signKey", VALID_KEY);

        assertDoesNotThrow(config::initSignKey);
    }
}
