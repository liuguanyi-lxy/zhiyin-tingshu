package com.atguigu.tingshu.common.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 随机数工具单元测试：验证位数与取值范围、随机抽样结果规模。
 */
class RandomUtilTest {

    @Test
    void getFourBitRandom_returnsFourDigits() {
        for (int i = 0; i < 20; i++) {
            String result = RandomUtil.getFourBitRandom();
            assertEquals(4, result.length());
            assertTrue(result.matches("\\d{4}"));
            int value = Integer.parseInt(result);
            assertTrue(value >= 0 && value < 10000);
        }
    }

    @Test
    void getSixBitRandom_returnsSixDigits() {
        for (int i = 0; i < 20; i++) {
            String result = RandomUtil.getSixBitRandom();
            assertEquals(6, result.length());
            assertTrue(result.matches("\\d{6}"));
            int value = Integer.parseInt(result);
            assertTrue(value >= 0 && value < 1000000);
        }
    }

    @Test
    void getRandom_extractsRequestedCountFromList() {
        List<String> source = Arrays.asList("a", "b", "c", "d", "e", "f", "g", "h");

        ArrayList result = RandomUtil.getRandom(source, 2);

        assertEquals(2, result.size());
        for (Object item : result) {
            assertTrue(source.contains(item));
        }
    }
}
