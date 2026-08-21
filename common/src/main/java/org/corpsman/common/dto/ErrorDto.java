package org.corpsman.common.dto;

import lombok.Getter;
import org.corpsman.common.exception.ErrorCode;

import java.time.LocalDateTime;

/**
 * Унифицированное представление ошибки, отдаваемое клиенту.
 */
@Getter
public class ErrorDto {

    private final String errorCode;
    private final String message;
    private final LocalDateTime timestamp;

    public ErrorDto(String errorCode, String message, LocalDateTime timestamp) {
        this.errorCode = errorCode;
        this.message = message;
        this.timestamp = timestamp;
    }

    /**
     * Создаёт DTO ошибки по коду и сообщению в текущий момент времени.
     *
     * @param errorCode код ошибки
     * @param message   человекочитаемое сообщение
     * @return DTO ошибки
     */
    public static ErrorDto of(ErrorCode errorCode, String message) {
        return new ErrorDto(errorCode.name(), message, LocalDateTime.now());
    }
}