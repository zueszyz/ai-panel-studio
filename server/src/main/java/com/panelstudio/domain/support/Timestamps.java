package com.panelstudio.domain.support;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 时间统一为 ISO-8601 UTC 字符串（如 {@code 2026-11-17T08:12:31.482Z}），
 * 字典序即时间序，避免 JVM 时区漂移。
 */
public final class Timestamps {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private Timestamps() {
    }

    public static String now() {
        return FORMATTER.format(Instant.now());
    }

    public static String format(Instant instant) {
        return FORMATTER.format(instant);
    }
}
