package com.atguigu.tingshu.user.strategy;

import com.atguigu.tingshu.common.execption.GuiguException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * 策略工厂单元测试：已注册类型返回对应策略，未注册类型抛 GuiguException。
 */
class StrategyContextTest {

    private StrategyContext context;
    private ItemTypeStrategy albumStrategy;

    @BeforeEach
    void injectStrategyMap() throws Exception {
        context = new StrategyContext();
        albumStrategy = mock(ItemTypeStrategy.class);
        Map<String, ItemTypeStrategy> strategyMap = new HashMap<>();
        strategyMap.put("1001", albumStrategy);
        Field field = StrategyContext.class.getDeclaredField("strategyMap");
        field.setAccessible(true);
        field.set(context, strategyMap);
    }

    @Test
    void registeredItemType_returnsStrategy() {
        assertEquals(albumStrategy, context.getStrategy("1001"));
    }

    @Test
    void unknownItemType_throwsGuiguException() {
        GuiguException ex = assertThrows(GuiguException.class, () -> context.getStrategy("9999"));
        assertEquals(500, ex.getCode());
        assertEquals("该策略9999实现类不存在", ex.getMessage());
    }
}
