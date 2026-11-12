package com.atguigu.tingshu.user.sms;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 校验阿里云 RPC 签名所需的关键编码规则与稳定性，不访问网络。 */
class AliyunRpcSignerTest {

    @Test
    void percentEncode_followsAliyunRules() {
        assertEquals("a%20b", AliyunRpcSigner.percentEncode("a b"));
        assertEquals("a%2Ab", AliyunRpcSigner.percentEncode("a*b"));
        assertEquals("a~b", AliyunRpcSigner.percentEncode("a~b"));
        assertEquals("%E7%9F%A5%E9%9F%B3", AliyunRpcSigner.percentEncode("知音"));
    }

    @Test
    void canonicalize_sortsKeysAndEncodesValues() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("b", "2");
        params.put("a", "x y");
        assertEquals("a=x%20y&b=2", AliyunRpcSigner.canonicalize(params));
    }

    @Test
    void signature_isDeterministicAndChangesWithParams() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("AccessKeyId", "id");
        params.put("Action", "SendSms");
        params.put("SignatureNonce", "nonce-1");

        String first = AliyunRpcSigner.sign(params, "secret");
        String second = AliyunRpcSigner.sign(params, "secret");
        assertEquals(first, second);
        assertFalse(first.isBlank());

        params.put("SignatureNonce", "nonce-2");
        assertNotEquals(first, AliyunRpcSigner.sign(params, "secret"));
    }

    @Test
    void signedQuery_containsSignatureAndIsSorted() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("Z", "last");
        params.put("A", "first");
        String query = AliyunRpcSigner.signedQuery(params, "secret");
        assertTrue(query.startsWith("A=first&"));
        assertTrue(query.contains("&Signature="));
        assertTrue(query.endsWith("&Z=last") || query.indexOf("&Z=last") > 0);
    }
}
