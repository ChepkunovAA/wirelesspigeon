package org.corpsman.common.exception;

import lombok.Getter;

/**
 * Базовое бизнес-исключение приложения.
 * <p>
 * Все доменные исключения должны наследоваться от него и нести {@link ErrorCode}.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * Создаёт исключение с кодом ошибки и стандартным сообщением кода.
     *
     * @param errorCode код ошибки
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /**
     * Создаёт исключение с кодом ошибки и переопределённым сообщением.
     *
     * @param errorCode код ошибки
     * @param message   переопределённое сообщение об ошибке
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}