package org.corpsman.webapp.config;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TimeUtilTest {

    private final TimeUtil timeUtil = new TimeUtil();

    @Test
    void isoUtcRendersUtcInstantWithZuluSuffix() {
        LocalDateTime utc = LocalDateTime.of(2026, 9, 6, 14, 46, 27, 725_242_000);
        assertThat(timeUtil.isoUtc(utc)).isEqualTo("2026-09-06T14:46:27.725242Z");
    }

    @Test
    void isoUtcRendersWholeSecondsWithoutFraction() {
        LocalDateTime utc = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
        assertThat(timeUtil.isoUtc(utc)).isEqualTo("2026-01-01T00:00:00Z");
    }

    @Test
    void isoUtcHandlesNull() {
        assertThat(timeUtil.isoUtc(null)).isNull();
    }
}