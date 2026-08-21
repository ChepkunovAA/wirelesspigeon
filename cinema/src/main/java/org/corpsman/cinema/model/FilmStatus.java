package org.corpsman.cinema.model;

/**
 * Статус жизненного цикла фильма.
 */
public enum FilmStatus {

    /**
     * В списке предложений пользователя.
     */
    PROPOSED,

    /**
     * Выбран для следующего просмотра.
     */
    SELECTED,

    /**
     * Уже просмотрен.
     */
    WATCHED,

    /**
     * Сброшен администратором при замене фильма.
     */
    DISCARDED
}