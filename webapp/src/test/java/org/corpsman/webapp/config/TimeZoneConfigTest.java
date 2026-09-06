package org.corpsman.webapp.config;

import java.time.Clock;
import java.time.ZoneId;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверки конфигурации системного времени (П.3, релиз 2.3.0).
 */
class TimeZoneConfigTest {

    private TimeZoneConfig configWith(String zone) {
        TimeZoneConfig config = new TimeZoneConfig();
        ReflectionTestUtils.setField(config, "timeZone", zone);
        return config;
    }

    @Test
    void clockUsesConfiguredZoneWhenSet() {
        TimeZoneConfig config = configWith("UTC");
        Clock clock = config.systemClock();
        assertThat(clock.getZone()).isEqualTo(ZoneId.of("UTC"));
    }

    @Test
    void clockUsesSystemZoneWhenBlank() {
        Clock clock = configWith("").systemClock();
        assertThat(clock.getZone()).isEqualTo(ZoneId.systemDefault());
    }

    @Test
    void applyDefaultTimeZoneSetsJvmDefaultWhenConfigured() {
        TimeZoneConfig config = configWith("Europe/Moscow");
        TimeZone before = TimeZone.getDefault();
        try {
            config.applyDefaultTimeZone();
            assertThat(TimeZone.getDefault().toZoneId()).isEqualTo(ZoneId.of("Europe/Moscow"));
        } finally {
            TimeZone.setDefault(before);
        }
    }
}
