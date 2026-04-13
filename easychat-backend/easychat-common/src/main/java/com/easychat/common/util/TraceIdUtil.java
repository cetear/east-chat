package com.easychat.common.util;

import java.util.UUID;

public class TraceIdUtil {
    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

    public static String generate() {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        TRACE_ID.set(traceId);
        return traceId;
    }

    public static String get() {
        String traceId = TRACE_ID.get();
        if (traceId == null) {
            traceId = generate();
        }
        return traceId;
    }

    public static void clear() {
        TRACE_ID.remove();
    }
}
