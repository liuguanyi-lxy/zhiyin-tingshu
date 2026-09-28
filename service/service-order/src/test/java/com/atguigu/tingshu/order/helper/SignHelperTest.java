package com.atguigu.tingshu.order.helper;

import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SignHelperTest {

    @Test
    void acceptsUntamperedOrderData() {
        Map<String, Object> parameters = parameters(System.currentTimeMillis(), "19.90");
        parameters.put("sign", SignHelper.getSign(new HashMap<>(parameters)));

        assertDoesNotThrow(() -> SignHelper.checkSign(parameters));
    }

    @Test
    void rejectsOrderAmountTampering() {
        Map<String, Object> parameters = parameters(System.currentTimeMillis(), "19.90");
        parameters.put("sign", SignHelper.getSign(new HashMap<>(parameters)));
        parameters.put("orderAmount", "0.01");

        GuiguException exception = assertThrows(GuiguException.class,
                () -> SignHelper.checkSign(parameters));

        assertEquals(ResultCodeEnum.SIGN_ERROR.getCode(), exception.getCode());
    }

    @Test
    void rejectsExpiredSignature() {
        Map<String, Object> parameters = parameters(System.currentTimeMillis() - 500_001, "19.90");
        parameters.put("sign", SignHelper.getSign(new HashMap<>(parameters)));

        GuiguException exception = assertThrows(GuiguException.class,
                () -> SignHelper.checkSign(parameters));

        assertEquals(ResultCodeEnum.SIGN_OVERDUE.getCode(), exception.getCode());
    }

    private Map<String, Object> parameters(long timestamp, String orderAmount) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("timestamp", timestamp);
        parameters.put("orderAmount", orderAmount);
        parameters.put("tradeNo", "trade-001");
        return parameters;
    }
}