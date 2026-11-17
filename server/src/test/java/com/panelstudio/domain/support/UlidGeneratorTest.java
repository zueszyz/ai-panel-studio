package com.panelstudio.domain.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UlidGeneratorTest {

    @Test
    @DisplayName("ULID 固定 26 位，只含 Crockford Base32 字符")
    void shouldGenerateFixedLength() {
        UlidGenerator generator = new UlidGenerator();
        String id = generator.next();
        assertThat(id).hasSize(26).matches("[0-9A-HJKMNP-TV-Z]{26}");
    }

    @Test
    @DisplayName("同一毫秒内单调递增，保证 ORDER BY id 与时间序一致")
    void shouldBeMonotonicWithinSameMillisecond() {
        UlidGenerator generator = new UlidGenerator();
        String previous = generator.next();
        Set<String> seen = new HashSet<>();
        seen.add(previous);
        for (int i = 0; i < 2000; i++) {
            String current = generator.next();
            assertThat(current).isGreaterThan(previous);
            assertThat(seen.add(current)).isTrue();
            previous = current;
        }
    }

    @Test
    @DisplayName("带前缀主键形如 dk_01H...")
    void shouldPrefixId() {
        UlidGenerator generator = new UlidGenerator();
        assertThat(generator.nextWithPrefix("dk")).startsWith("dk_").hasSize(29);
    }
}
