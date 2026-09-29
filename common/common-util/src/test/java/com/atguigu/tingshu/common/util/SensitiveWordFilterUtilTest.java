package com.atguigu.tingshu.common.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 敏感词 DFA 过滤单元测试。
 * 注意：sensitiveWordMap 是静态可变状态，每个用例前后重置，避免用例间污染。
 */
class SensitiveWordFilterUtilTest {

    @BeforeEach
    @AfterEach
    void resetDfaModel() throws Exception {
        Field field = SensitiveWordFilterUtil.class.getDeclaredField("sensitiveWordMap");
        field.setAccessible(true);
        field.set(null, null);
    }

    @Test
    void withoutBuiltModel_returnsOriginalText() {
        assertEquals("普通文本内容", SensitiveWordFilterUtil.replaceSensitiveWord("普通文本内容"));
    }

    @Test
    void builtModel_replacesSensitiveWord() {
        SensitiveWordFilterUtil.buildDFAModel(Arrays.asList("黄色", "自杀"));

        assertEquals("这本**书不能看", SensitiveWordFilterUtil.replaceSensitiveWord("这本黄色书不能看"));
    }

    @Test
    void builtModel_keepsCleanTextUntouched() {
        SensitiveWordFilterUtil.buildDFAModel(Arrays.asList("黄色", "自杀"));

        assertEquals("这是一段干净的话", SensitiveWordFilterUtil.replaceSensitiveWord("这是一段干净的话"));
    }

    @Test
    void builtModel_replacesEveryOccurrence() {
        SensitiveWordFilterUtil.buildDFAModel(Collections.singletonList("敏感词"));

        String result = SensitiveWordFilterUtil.replaceSensitiveWord("敏感词A敏感词B敏感词");

        assertEquals("***A***B***", result);
    }

    @Test
    void builtModel_filtersSingleCharWord() {
        //【回归锁】原实现 matchFlag < 2 导致单字敏感词永远无法命中，已修复
        SensitiveWordFilterUtil.buildDFAModel(Collections.singletonList("笨"));

        String result = SensitiveWordFilterUtil.replaceSensitiveWord("这个笨产品");

        assertFalse(result.contains("笨"));
        assertEquals("这个*产品", result);
    }

    @Test
    void builtModel_longerWordMatchesAsWhole() {
        // 词库同时含"自杀"与"自杀指南"时，最小匹配按命中的第一个完整词替换
        SensitiveWordFilterUtil.buildDFAModel(Arrays.asList("自杀", "自杀指南"));

        String result = SensitiveWordFilterUtil.replaceSensitiveWord("搜索自杀指南");

        // 无论命中哪个词，原敏感内容都不允许原样输出
        assertEquals(false, result.contains("自杀"));
    }
}
