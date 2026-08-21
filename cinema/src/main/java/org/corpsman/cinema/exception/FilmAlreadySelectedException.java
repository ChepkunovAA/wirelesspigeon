package org.corpsman.cinema.exception;

import lombok.Getter;
import org.corpsman.common.exception.BusinessException;
import org.corpsman.common.exception.ErrorCode;

/**
 * Исключение: фильм уже выбран для следующего просмотра.
 */
@Getter
public class FilmAlreadySelectedException extends BusinessException {

    private final Long filmId;
    private final String filmTitle;

    /**
     * Создаёт исключение с данными уже выбранного фильма.
     *
     * @param filmId    идентификатор фильма
     * @param filmTitle название фильма
     */
    public FilmAlreadySelectedException(Long filmId, String filmTitle) {
        super(ErrorCode.FILM_ALREADY_SELECTED, "Фильм '" + filmTitle + "' уже выбран");
        this.filmId = filmId;
        this.filmTitle = filmTitle;
    }
}