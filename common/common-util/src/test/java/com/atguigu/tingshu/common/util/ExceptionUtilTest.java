package com.atguigu.tingshu.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ExceptionUtil 单元测试：异常堆栈转字符串。
 */
class ExceptionUtilTest {

    @Test
    void getErrorMessage_containsExceptionClassAndMessage() {
        Exception exception = new IllegalStateException("业务处理失败");

        String message = ExceptionUtil.getErrorMessage(exception);

        assertTrue(message.contains("java.lang.IllegalStateException"));
        assertTrue(message.contains("业务处理失败"));
        assertTrue(message.contains("at com.atguigu.tingshu.common.util.ExceptionUtilTest"));
    }

    @Test
    void getErrorMessage_multilineStackTrace() {
        Exception exception = new RuntimeException("root");

        String message = ExceptionUtil.getErrorMessage(exception);

        // 至少包含首行与一行堆栈帧
        assertFalse(message.isEmpty());
        assertTrue(message.lines().count() >= 2);
    }
}
