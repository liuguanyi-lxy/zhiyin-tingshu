package com.atguigu.tingshu.common.util;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * 链路追踪工具类：traceId 的生成与 MDC 管理。
 * <p>
 * 一次请求从网关穿透到各微服务、再到 Feign 下游，用同一个 traceId 串联所有日志，
 * 便于跨服务排查问题。traceId 通过 HTTP header {@code X-Request-Id} 透传，
 * 日志通过 MDC key {@code traceId} 输出（logback pattern 用 {@code [%X{traceId:-}]}）。
 * </p>
 * <p>
 * 网关（WebFlux）与服务端（servlet）均依赖 common-util，故放在此处供两边共用。
 * </p>
 */
public final class TraceContextUtil {

    /** 日志 pattern 中输出的 MDC key */
    public static final String TRACE_ID = "traceId";

    /** 跨服务传递 traceId 的 HTTP header 名 */
    public static final String HEADER_TRACE_ID = "X-Request-Id";

    private TraceContextUtil() {
    }

    /**
     * 生成 32 位十六进制 traceId
     */
    public static String generateId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 获取当前线程 MDC 中的 traceId，没有则返回 null
     */
    public static String getTraceId() {
        return MDC.get(TRACE_ID);
    }

    /**
     * 生成新 traceId 并写入 MDC（用于 MQ 消费、定时任务等无 HTTP 请求头的线程）
     *
     * @return 新生成的 traceId
     */
    public static String begin() {
        String id = generateId();
        MDC.put(TRACE_ID, id);
        return id;
    }

    /**
     * 传入已有 traceId 并写入 MDC（用于从请求头/上游透传的场景），
     * 为空则自动生成新 traceId。
     *
     * @param id 上游透传的 traceId，可为空
     * @return 实际写入的 traceId
     */
    public static String begin(String id) {
        if (id == null || id.isEmpty()) {
            return begin();
        }
        MDC.put(TRACE_ID, id);
        return id;
    }

    /**
     * 清理 MDC 中的 traceId，必须在 finally 中调用，防止线程池复用导致串号
     */
    public static void end() {
        MDC.remove(TRACE_ID);
    }
}
