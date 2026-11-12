package com.atguigu.tingshu.user.sms;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 阿里云 RPC 风格接口签名（HMAC-SHA1）。不依赖阿里云 SDK，避免引入额外仓库依赖。
 * 规范：参数按 RFC3986 编码后按 key 排序，StringToSign = "GET&%2F&" + 编码后的查询串。
 */
public final class AliyunRpcSigner {

    private AliyunRpcSigner() {
    }

    public static String percentEncode(String value) {
        if (value == null) {
            return "";
        }
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8)
                    .replace("+", "%20")
                    .replace("*", "%2A")
                    .replace("%7E", "~");
        } catch (Exception e) {
            throw new IllegalStateException("参数编码失败", e);
        }
    }

    public static String canonicalize(Map<String, String> params) {
        return new TreeMap<>(params).entrySet().stream()
                .map(entry -> percentEncode(entry.getKey()) + "=" + percentEncode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    public static String sign(Map<String, String> params, String accessKeySecret) {
        String stringToSign = "GET&%2F&" + percentEncode(canonicalize(params));
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec((accessKeySecret + "&").getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("短信请求签名失败", e);
        }
    }

    /** 签名后的完整查询串，键按字典序输出，便于请求与测试复现。 */
    public static String signedQuery(Map<String, String> params, String accessKeySecret) {
        Map<String, String> signed = new TreeMap<>(params);
        signed.put("Signature", sign(signed, accessKeySecret));
        return canonicalize(signed);
    }
}
