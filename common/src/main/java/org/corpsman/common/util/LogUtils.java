package org.corpsman.common.util;

/**
 * Утилиты форматирования логов.
 */
public final class LogUtils {

    private LogUtils() {
    }

    /**
     * Возвращает текстовый контекст пользователя для строки лога.
     *
     * @param userId идентификатор пользователя
     * @return строка контекста
     */
    public static String userContext(Long userId) {
        return userId == null ? "anonymous" : "user=" + userId;
    }

    /**
     * Маскирует значение, оставляя только первые символы.
     *
     * @param value     исходное значение
     * @param visibleCh количество видимых символов
     * @return замаскированное значение
     */
    public static String mask(String value, int visibleCh) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        int keep = Math.min(value.length(), Math.max(0, visibleCh));
        return value.substring(0, keep) + "...";
    }
}