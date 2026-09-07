package org.corpsman.webapp.config;

import java.time.Clock;
import java.time.ZoneId;
import java.util.TimeZone;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация системного времени приложения.
 *
 * <p>Все времена в приложении — {@link java.time.LocalDateTime}, сохранённые и
 * выводимые как <b>wall-clock UTC</b> (конвенция хранения). Таймзона JVM здесь
 * делается явной и детерминированной: если задан {@code app.time-zone}, она
 * принудительно устанавливается как таймзона JVM по умолчанию; иначе остаётся
 * системная таймзона хоста. Для детерминированного хранения UTC в продакшене
 * параметр {@code app.time-zone} задаётся как {@code UTC} (см. application.yml).</p>
 *
 * <p>Отображение: сервер выводит UTC-значение как фоллбэк без JS
 * ({@code #temporals.format}), а {@code time-format.js} переводит каждый
 * {@code <time data-utc="...Z">} в часовой пояс браузера пользователя
 * (вариант «время в таймзоне браузера», П.7 релиза 2.4.0).</p>
 */
@Configuration
public class TimeZoneConfig {

    private static final Logger log = LoggerFactory.getLogger(TimeZoneConfig.class);

    /** Явная таймзона (например {@code UTC}, {@code Europe/Moscow}); пустая — системная. */
    @Value("${app.time-zone:}")
    private String timeZone;

    /**
     * Применяет таймзону JVM по умолчанию до начала обработки запросов.
     */
    @PostConstruct
    public void applyDefaultTimeZone() {
        ZoneId zone = ZoneId.systemDefault();
        if (timeZone != null && !timeZone.isBlank()) {
            zone = ZoneId.of(timeZone);
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
        }
        log.info("Системное время приложения: таймзона JVM = {}", zone);
    }

    /**
     * Часы системного времени для детерминированного получения текущего момента.
     *
     * @return системные часы в таймзоне приложения
     */
    @Bean
    public Clock systemClock() {
        ZoneId zone = (timeZone != null && !timeZone.isBlank())
                ? ZoneId.of(timeZone)
                : ZoneId.systemDefault();
        return Clock.system(zone);
    }
}
