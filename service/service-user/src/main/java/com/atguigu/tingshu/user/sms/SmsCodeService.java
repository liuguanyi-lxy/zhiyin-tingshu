package com.atguigu.tingshu.user.sms;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Set;

/**
 * 短信验证码：发送频控、有效期、一次性消费、错误次数限制。
 * 未配置凭证时直接拒绝，不产生“假发送成功”。
 */
@Service
public class SmsCodeService {

    public static final String SCENE_REGISTER = "register";
    public static final String SCENE_LOGIN = "login";
    private static final Set<String> SCENES = Set.of(SCENE_REGISTER, SCENE_LOGIN);

    private final SmsProperties properties;
    private final SmsClient smsClient;
    private final StringRedisTemplate redisTemplate;
    private final SecureRandom random = new SecureRandom();

    public SmsCodeService(SmsProperties properties, SmsClient smsClient, StringRedisTemplate redisTemplate) {
        this.properties = properties;
        this.smsClient = smsClient;
        this.redisTemplate = redisTemplate;
    }

    /** 只有开关打开且 AccessKey/签名/模板齐全，验证码登录与注册校验才生效。 */
    public boolean isEnabled() {
        return properties.isConfigured();
    }

    public void sendCode(String phone, String scene) {
        checkPhone(phone);
        checkScene(scene);
        if (!isEnabled()) {
            throw new IllegalStateException("短信服务未配置，暂时无法发送验证码");
        }

        String cooldownKey = "sms:cooldown:" + phone;
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(cooldownKey, "1", Duration.ofSeconds(properties.getResendSeconds()));
        if (!Boolean.TRUE.equals(acquired)) {
            throw new IllegalStateException("验证码发送过于频繁，请稍后再试");
        }

        String dailyKey = "sms:daily:" + phone + ":" + LocalDate.now();
        Long daily = redisTemplate.opsForValue().increment(dailyKey);
        if (daily != null && daily == 1L) {
            redisTemplate.expire(dailyKey, Duration.ofDays(1));
        }
        if (daily != null && daily > properties.getDailyLimit()) {
            throw new IllegalStateException("今日验证码发送次数已达上限");
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        String codeKey = codeKey(scene, phone);
        redisTemplate.opsForValue().set(codeKey, code, Duration.ofSeconds(properties.getCodeTtlSeconds()));
        redisTemplate.delete(attemptKey(scene, phone));
        try {
            smsClient.sendCode(phone, code);
        } catch (RuntimeException e) {
            // 发送失败不保留验证码，并允许用户立即重试。
            redisTemplate.delete(codeKey);
            redisTemplate.delete(cooldownKey);
            throw e;
        }
    }

    public void verify(String phone, String scene, String code) {
        checkPhone(phone);
        checkScene(scene);
        if (!isEnabled()) {
            throw new IllegalStateException("短信服务未配置，无法校验验证码");
        }
        if (code == null || !code.matches("^\\d{6}$")) {
            throw new IllegalStateException("请输入 6 位短信验证码");
        }

        String codeKey = codeKey(scene, phone);
        String saved = redisTemplate.opsForValue().get(codeKey);
        if (saved == null) {
            throw new IllegalStateException("验证码已过期，请重新获取");
        }

        String attemptKey = attemptKey(scene, phone);
        Long attempts = redisTemplate.opsForValue().increment(attemptKey);
        if (attempts != null && attempts == 1L) {
            redisTemplate.expire(attemptKey, Duration.ofSeconds(properties.getCodeTtlSeconds()));
        }
        if (attempts != null && attempts > properties.getMaxAttempts()) {
            redisTemplate.delete(codeKey);
            throw new IllegalStateException("验证码错误次数过多，请重新获取");
        }

        boolean matched = MessageDigest.isEqual(
                saved.getBytes(StandardCharsets.UTF_8), code.getBytes(StandardCharsets.UTF_8));
        if (!matched) {
            throw new IllegalStateException("验证码错误");
        }
        // 一次性消费：成功后立即失效，重复提交不会再次通过。
        redisTemplate.delete(codeKey);
        redisTemplate.delete(attemptKey);
    }

    private static void checkPhone(String phone) {
        if (phone == null || !phone.matches("^1[3-9]\\d{9}$")) {
            throw new IllegalStateException("手机号格式不正确");
        }
    }

    private static void checkScene(String scene) {
        if (!SCENES.contains(scene)) {
            throw new IllegalStateException("不支持的验证码场景");
        }
    }

    private static String codeKey(String scene, String phone) {
        return "sms:code:" + scene + ":" + phone;
    }

    private static String attemptKey(String scene, String phone) {
        return "sms:attempts:" + scene + ":" + phone;
    }
}
