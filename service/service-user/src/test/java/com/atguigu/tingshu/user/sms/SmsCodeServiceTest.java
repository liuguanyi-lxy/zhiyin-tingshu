package com.atguigu.tingshu.user.sms;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 纯 Mockito：不连接 Redis、不发真实短信。 */
@ExtendWith(MockitoExtension.class)
class SmsCodeServiceTest {

    private static final String PHONE = "13800001111";

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private SmsClient smsClient;

    private SmsProperties properties;
    private SmsCodeService service;

    @BeforeEach
    void setUp() {
        properties = new SmsProperties();
        properties.setEnabled(true);
        properties.setAccessKeyId("fixture-id");
        properties.setAccessKeySecret("fixture-secret");
        properties.setSignName("知音听书");
        properties.setTemplateCode("SMS_000000");
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new SmsCodeService(properties, smsClient, redisTemplate);
    }

    @Test
    void disabledService_rejectsWithoutTouchingRedisOrProvider() {
        properties.setEnabled(false);
        SmsCodeService disabled = new SmsCodeService(properties, smsClient, redisTemplate);
        assertThrows(IllegalStateException.class, () -> disabled.sendCode(PHONE, SmsCodeService.SCENE_LOGIN));
        verify(smsClient, never()).sendCode(anyString(), anyString());
        verify(valueOperations, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void missingCredentials_isTreatedAsDisabled() {
        properties.setTemplateCode("");
        assertThrows(IllegalStateException.class, () -> service.sendCode(PHONE, SmsCodeService.SCENE_LOGIN));
        verify(smsClient, never()).sendCode(anyString(), anyString());
    }

    @Test
    void invalidPhoneOrScene_isRejected() {
        assertThrows(IllegalStateException.class, () -> service.sendCode("123", SmsCodeService.SCENE_LOGIN));
        assertThrows(IllegalStateException.class, () -> service.sendCode(PHONE, "reset"));
        verify(smsClient, never()).sendCode(anyString(), anyString());
    }

    @Test
    void cooldown_blocksResend() {
        when(valueOperations.setIfAbsent(eq("sms:cooldown:" + PHONE), eq("1"), any(Duration.class)))
                .thenReturn(false);
        assertThrows(IllegalStateException.class, () -> service.sendCode(PHONE, SmsCodeService.SCENE_LOGIN));
        verify(smsClient, never()).sendCode(anyString(), anyString());
    }

    @Test
    void dailyLimit_blocksBeforeSending() {
        when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
        when(valueOperations.increment(anyString())).thenReturn(11L);
        assertThrows(IllegalStateException.class, () -> service.sendCode(PHONE, SmsCodeService.SCENE_LOGIN));
        verify(smsClient, never()).sendCode(anyString(), anyString());
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void send_storesSixDigitCodeAndCallsProvider() {
        when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
        when(valueOperations.increment(anyString())).thenReturn(1L);

        service.sendCode(PHONE, SmsCodeService.SCENE_REGISTER);

        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(smsClient).sendCode(eq(PHONE), code.capture());
        assertTrue(code.getValue().matches("^\\d{6}$"));
        verify(valueOperations).set(eq("sms:code:register:" + PHONE), eq(code.getValue()), eq(Duration.ofSeconds(300)));
        verify(redisTemplate).expire(anyString(), eq(Duration.ofDays(1)));
    }

    @Test
    void providerFailure_removesCodeAndCooldown() {
        when(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
        when(valueOperations.increment(anyString())).thenReturn(1L);
        org.mockito.Mockito.doThrow(new IllegalStateException("provider down"))
                .when(smsClient).sendCode(anyString(), anyString());

        assertThrows(IllegalStateException.class, () -> service.sendCode(PHONE, SmsCodeService.SCENE_LOGIN));
        verify(redisTemplate).delete("sms:code:login:" + PHONE);
        verify(redisTemplate).delete("sms:cooldown:" + PHONE);
    }

    @Test
    void verify_success_consumesCodeOnce() {
        when(valueOperations.get("sms:code:login:" + PHONE)).thenReturn("123456");
        when(valueOperations.increment("sms:attempts:login:" + PHONE)).thenReturn(1L);

        service.verify(PHONE, SmsCodeService.SCENE_LOGIN, "123456");

        verify(redisTemplate).delete("sms:code:login:" + PHONE);
        verify(redisTemplate).delete("sms:attempts:login:" + PHONE);
    }

    @Test
    void verify_wrongCode_countsAttempt() {
        when(valueOperations.get("sms:code:login:" + PHONE)).thenReturn("123456");
        when(valueOperations.increment("sms:attempts:login:" + PHONE)).thenReturn(1L);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.verify(PHONE, SmsCodeService.SCENE_LOGIN, "654321"));
        assertEquals("验证码错误", error.getMessage());
        verify(redisTemplate, never()).delete("sms:code:login:" + PHONE);
    }

    @Test
    void verify_tooManyAttempts_invalidatesCode() {
        when(valueOperations.get("sms:code:login:" + PHONE)).thenReturn("123456");
        when(valueOperations.increment("sms:attempts:login:" + PHONE)).thenReturn(6L);

        assertThrows(IllegalStateException.class,
                () -> service.verify(PHONE, SmsCodeService.SCENE_LOGIN, "123456"));
        verify(redisTemplate).delete("sms:code:login:" + PHONE);
    }

    @Test
    void verify_expiredCode_fails() {
        when(valueOperations.get("sms:code:login:" + PHONE)).thenReturn(null);
        assertThrows(IllegalStateException.class,
                () -> service.verify(PHONE, SmsCodeService.SCENE_LOGIN, "123456"));
        verify(valueOperations, never()).increment(anyString());
    }

    @Test
    void verify_malformedCode_failsBeforeReadingRedis() {
        assertThrows(IllegalStateException.class,
                () -> service.verify(PHONE, SmsCodeService.SCENE_LOGIN, "12ab"));
        verify(valueOperations, never()).get(anyString());
    }
}
