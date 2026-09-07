package org.corpsman.webapp.config;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.stereotype.Component;

/**
 * Утилита рендеринга времени.
 *
 * <p>Все времена приложения хранятся как {@link LocalDateTime} в UTC (wall-clock
 * UTC). Сервер выводит их как фоллбэк в шаблонах ({@code #temporals.format}),
 * а {@code time-format.js} переводит в часовой пояс браузера пользователя.
 * Данный бин (Spring-имя {@code timeUtil}) отдаёт абсолютный момент в ISO-8601
 * (суффикс {@code Z}), по которому клиент вычисляет локальное время.</p>
 */
@Component("timeUtil")
public class TimeUtil {

    /**
     * Абсолютный момент в ISO-8601 UTC для переданного UTC-времени.
     *
     * @param ldt хранимое время (wall-clock UTC) или {@code null}
     * @return ISO-8601 со суффиксом {@code Z}, либо {@code null}
     */
    public String isoUtc(LocalDateTime ldt) {
        return ldt == null ? null : ldt.toInstant(ZoneOffset.UTC).toString();
    }
}