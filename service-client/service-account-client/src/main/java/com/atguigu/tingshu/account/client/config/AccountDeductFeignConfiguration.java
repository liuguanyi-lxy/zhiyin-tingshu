package com.atguigu.tingshu.account.client.config;

import com.atguigu.tingshu.common.constant.AccountInternalConstant;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/** 仅由指定 Feign 客户端导入，不加 @Configuration，避免被扫描为全局拦截器。 */
public class AccountDeductFeignConfiguration {
    @Bean
    public RequestInterceptor accountDeductInterceptor(
            @Value("${tingshu.account.internal-token:${TINGSHU_ACCOUNT_INTERNAL_TOKEN:}}") String internalToken) {
        return template -> {
            String path = template.path();
            String normalizedPath = path.startsWith("/") ? path : "/" + path;
            boolean deduction = AccountInternalConstant.DEDUCT_PATH.equals(normalizedPath);
            boolean accountInitialization = normalizedPath.startsWith(AccountInternalConstant.INIT_ACCOUNT_PATH_PREFIX)
                    && normalizedPath.substring(AccountInternalConstant.INIT_ACCOUNT_PATH_PREFIX.length())
                            .matches("[1-9][0-9]*");
            if (!deduction && !accountInitialization) {
                return;
            }
            if (!StringUtils.hasText(internalToken) || internalToken.length() < 32) {
                throw new IllegalStateException("Internal account credential is missing or too short");
            }
            template.removeHeader(AccountInternalConstant.HEADER);
            template.header(AccountInternalConstant.HEADER, internalToken);
        };
    }
}
