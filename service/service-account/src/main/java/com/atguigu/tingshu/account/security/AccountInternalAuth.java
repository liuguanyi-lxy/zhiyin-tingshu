package com.atguigu.tingshu.account.security;

import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class AccountInternalAuth {
    private final String internalToken;

    public AccountInternalAuth(
            @Value("${tingshu.account.internal-token:${TINGSHU_ACCOUNT_INTERNAL_TOKEN:}}") String internalToken) {
        this.internalToken = internalToken;
    }

    public void requireTrustedCaller(String suppliedToken) {
        // 凭证缺失或过短时拒绝调用，不降级为用户登录鉴权。
        if (!StringUtils.hasText(internalToken) || internalToken.length() < 32
                || !StringUtils.hasText(suppliedToken)
                || !MessageDigest.isEqual(internalToken.getBytes(StandardCharsets.UTF_8),
                        suppliedToken.getBytes(StandardCharsets.UTF_8))) {
            throw new GuiguException(ResultCodeEnum.PERMISSION);
        }
    }
}
