package org.corpsman.cinema.exception;

import org.corpsman.common.exception.BusinessException;
import org.corpsman.common.exception.ErrorCode;

/**
 * Исключение: нет доступных фильмов для выбора.
 */
public class NoFilmsAvailableException extends BusinessException {

    /**
     * Создаёт исключение об отсутствии доступных фильмов.
     */
    public NoFilmsAvailableException() {
        super(ErrorCode.NO_FILMS_AVAILABLE);
    }
}