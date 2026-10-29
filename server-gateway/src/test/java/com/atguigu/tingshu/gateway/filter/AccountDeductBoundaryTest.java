package com.atguigu.tingshu.gateway.filter;

import com.atguigu.tingshu.common.constant.AccountInternalConstant;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class AccountDeductBoundaryTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "/api/account/userAccount/checkAndDeduct",
            "/api/account/userAccount/checkAndDeduct/",
            "/api/account/userAccount/checkAndDeduct;fake=1",
            "/api//account/userAccount/checkAndDeduct",
            "/api/account/userAccount/%63heckAndDeduct"
    })
    void externalDeductionIsNeverForwardedEvenWithHeaders(String path) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post(path)
                .header("token", "ordinary-user-token")
                .header(AccountInternalConstant.HEADER, "forged-internal-token"));
        AtomicBoolean forwarded = new AtomicBoolean(false);
        new AuthGlobalFilter().filter(exchange, ignored -> {
            forwarded.set(true);
            return Mono.empty();
        }).block();
        assertFalse(forwarded.get());
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }
}
