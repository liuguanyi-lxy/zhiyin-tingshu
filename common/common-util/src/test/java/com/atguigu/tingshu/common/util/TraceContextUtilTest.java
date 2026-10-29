package com.atguigu.tingshu.common.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 链路追踪上下文单元测试：traceId 生成、MDC 生命周期、上游透传复用。
 */
class TraceContextUtilTest {

    @AfterEach
    void cleanUp() {
        TraceContextUtil.end();
    }

    @Test
    void generateId_returns32HexChars() {
        String id = TraceContextUtil.generateId();
        assertEquals(32, id.length());
        assertTrue(id.matches("[0-9a-f]{32}"));
    }

    @Test
    void generateId_isUnique() {
        assertNotEquals(TraceContextUtil.generateId(), TraceContextUtil.generateId());
    }

    @Test
    void begin_writesTraceIdIntoMdc() {
        String id = TraceContextUtil.begin();
        assertEquals(id, TraceContextUtil.getTraceId());
    }

    @Test
    void end_clearsTraceId() {
        TraceContextUtil.begin();
        TraceContextUtil.end();
        assertNull(TraceContextUtil.getTraceId());
    }

    @Test
    void beginWithUpstreamId_reusesIt() {
        assertEquals("upstream-id", TraceContextUtil.begin("upstream-id"));
        assertEquals("upstream-id", TraceContextUtil.getTraceId());
    }

    @Test
    void beginWithNullOrBlank_generatesNewId() {
        String fromNull = TraceContextUtil.begin(null);
        assertEquals(fromNull, TraceContextUtil.getTraceId());
        assertEquals(32, fromNull.length());

        TraceContextUtil.end();
        String fromBlank = TraceContextUtil.begin("");
        assertEquals(32, fromBlank.length());
        assertEquals(fromBlank, TraceContextUtil.getTraceId());
    }
}
