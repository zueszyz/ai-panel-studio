package com.panelstudio.domain.support;

import java.security.SecureRandom;

/**
 * ULID 生成器：48 位毫秒时间戳 + 80 位随机数，Crockford Base32 编码，26 字符。
 * 同一毫秒内保证单调递增（随机部分 +1），因此 {@code ORDER BY id} 与时间序一致。
 * 纯函数式实现，无 Spring 依赖，可单测。
 */
public final class UlidGenerator {

    private static final char[] ENCODING = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int TIME_LEN = 10;
    private static final int RANDOM_LEN = 16;

    private final SecureRandom random = new SecureRandom();
    private long lastTimestamp = -1L;
    private final byte[] lastRandom = new byte[10];

    public synchronized String next() {
        long now = System.currentTimeMillis();
        if (now <= lastTimestamp) {
            now = lastTimestamp;
            incrementRandom();
        } else {
            lastTimestamp = now;
            random.nextBytes(lastRandom);
        }
        return encode(now, lastRandom);
    }

    /** 生成带前缀主键，如 {@code dk_01H...}。 */
    public String nextWithPrefix(String prefix) {
        return prefix + "_" + next();
    }

    private void incrementRandom() {
        for (int i = lastRandom.length - 1; i >= 0; i--) {
            int v = (lastRandom[i] & 0xFF) + 1;
            lastRandom[i] = (byte) v;
            if (v <= 0xFF) {
                return;
            }
        }
        // 全部进位溢出（概率可忽略）：重置为随机数
        random.nextBytes(lastRandom);
    }

    private String encode(long timestamp, byte[] rand) {
        char[] out = new char[TIME_LEN + RANDOM_LEN];
        long time = timestamp;
        for (int i = TIME_LEN - 1; i >= 0; i--) {
            out[i] = ENCODING[(int) (time & 0x1F)];
            time >>>= 5;
        }
        int bitBuffer = 0;
        int bitCount = 0;
        int idx = TIME_LEN;
        for (byte b : rand) {
            bitBuffer = (bitBuffer << 8) | (b & 0xFF);
            bitCount += 8;
            while (bitCount >= 5) {
                bitCount -= 5;
                out[idx++] = ENCODING[(bitBuffer >>> bitCount) & 0x1F];
            }
        }
        if (bitCount > 0) {
            out[idx] = ENCODING[(bitBuffer << (5 - bitCount)) & 0x1F];
        }
        return new String(out);
    }
}
