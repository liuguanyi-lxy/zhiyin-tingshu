package com.atguigu.tingshu.user.sms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 阿里云短信 SendSms（RPC 2017-05-25）直连实现。
 * 只发送验证码模板；代码、AccessKey 不写入日志。
 */
@Component
@Slf4j
public class AliyunSmsClient implements SmsClient {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private final SmsProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public AliyunSmsClient(SmsProperties properties) {
        this.properties = properties;
    }

    @Override
    public void sendCode(String phone, String code) {
        if (!properties.isConfigured()) {
            throw new IllegalStateException("短信服务未配置，无法发送验证码");
        }
        Map<String, String> params = new LinkedHashMap<>();
        params.put("AccessKeyId", properties.getAccessKeyId());
        params.put("Action", "SendSms");
        params.put("Format", "JSON");
        params.put("PhoneNumbers", phone);
        params.put("RegionId", properties.getRegionId());
        params.put("SignName", properties.getSignName());
        params.put("SignatureMethod", "HMAC-SHA1");
        params.put("SignatureNonce", UUID.randomUUID().toString());
        params.put("SignatureVersion", "1.0");
        params.put("TemplateCode", properties.getTemplateCode());
        params.put("TemplateParam", "{\"code\":\"" + code + "\"}");
        params.put("Timestamp", TIMESTAMP.format(Instant.now()));
        params.put("Version", "2017-05-25");

        String query = AliyunRpcSigner.signedQuery(params, properties.getAccessKeySecret());
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getEndpoint() + "?" + query))
                .timeout(Duration.ofSeconds(8))
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode body = objectMapper.readTree(response.body());
            String resultCode = body.path("Code").asText("");
            if (response.statusCode() != 200 || !"OK".equals(resultCode)) {
                log.warn("[短信] 发送失败 phone={} http={} code={} message={}",
                        mask(phone), response.statusCode(), resultCode, body.path("Message").asText(""));
                throw new IllegalStateException("验证码发送失败，请稍后重试");
            }
            log.info("[短信] 验证码已发送 phone={} requestId={}", mask(phone), body.path("RequestId").asText(""));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("验证码发送被中断，请重试", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[短信] 发送异常 phone={} type={}", mask(phone), e.getClass().getSimpleName());
            throw new IllegalStateException("验证码发送失败，请稍后重试", e);
        }
    }

    private static String mask(String phone) {
        if (phone == null || phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
