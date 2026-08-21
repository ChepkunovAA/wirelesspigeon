package org.corpsman.common.event;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Событие добавления фильма в кинотеатр.
 */
@Getter
public class FilmAddedEvent extends BaseEvent {

    private final Long filmId;
    private final String filmTitle;

    /**
     * Создаёт событие добавления фильма.
     *
     * @param filmId      идентификатор добавленного фильма
     * @param filmTitle   название фильма
     * @param userId      идентификатор добавившего пользователя
     * @param occurredAt  момент добавления
     */
    public FilmAddedEvent(Long filmId, String filmTitle, Long userId, LocalDateTime occurredAt) {
        super(userId, occurredAt);
        this.filmId = filmId;
        this.filmTitle = filmTitle;
    }
}