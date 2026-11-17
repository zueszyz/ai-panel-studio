package com.panelstudio.application;

import com.panelstudio.api.advice.ApiException;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * 不透明游标：内部是 offset，外部只暴露 base64 字符串。
 * 排序含状态优先级，无法用 ULID 做纯 id 游标，因此显式记录偏移量。
 */
final class CursorCodec {

    private static final String PREFIX = "o:";

    private CursorCodec() {
    }

    static String encode(int offset) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((PREFIX + offset).getBytes(StandardCharsets.UTF_8));
    }

    static int decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            if (!raw.startsWith(PREFIX)) {
                throw new IllegalArgumentException("cursor 前缀不匹配");
            }
            int offset = Integer.parseInt(raw.substring(PREFIX.length()));
            if (offset < 0) {
                throw new IllegalArgumentException("cursor 不能为负");
            }
            return offset;
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "分页游标不合法",
                    Map.of("field", "cursor"));
        }
    }
}
