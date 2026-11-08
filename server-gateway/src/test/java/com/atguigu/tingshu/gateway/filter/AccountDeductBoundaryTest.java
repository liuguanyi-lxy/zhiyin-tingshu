package com.atguigu.tingshu.gateway.filter;

import com.atguigu.tingshu.common.constant.AccountInternalConstant;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AccountDeductBoundaryTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "/api/account/userAccount/checkAndDeduct",
            "/api/account/userAccount/checkAndDeduct/",
            "/api/account/userAccount/checkAndDeduct;fake=1",
            "/api//account/userAccount/checkAndDeduct",
            "/api/account/userAccount/%63heckAndDeduct",
            "/api/account/userAccount/addUserAccount/8",
            "/api/account/userAccount/addUserAccount/8;fake=1",
            "/api//account/userAccount/addUserAccount/8",
            "/api/account/userAccount/addUserAccount/%38"
    })
    void externalInternalAccountEndpointsAreNeverForwardedEvenWithHeaders(String path) {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.method(HttpMethod.POST, URI.create(path))
                .header("token", "ordinary-user-token")
                .header(AccountInternalConstant.HEADER, "forged-internal-token"));
        AtomicBoolean forwarded = new AtomicBoolean(false);
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        when(redis.hasKey(any())).thenReturn(Mono.just(true));
        AuthGlobalFilter filter = new AuthGlobalFilter();
        ReflectionTestUtils.setField(filter, "redisTemplate", redis);
        ReflectionTestUtils.setField(filter, "objectMapper", new ObjectMapper());
        filter.filter(exchange, ignored -> {
            forwarded.set(true);
            return Mono.empty();
        }).block();
        assertFalse(forwarded.get());
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }
}
