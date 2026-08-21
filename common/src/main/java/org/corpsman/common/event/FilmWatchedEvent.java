package org.corpsman.common.event;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Событие перевода фильма в статус просмотренного.
 */
@Getter
public class FilmWatchedEvent extends BaseEvent {

    private final Long filmId;

    /**
     * Создаёт событие просмотра фильма.
     *
     * @param filmId     идентификатор просмотренного фильма
     * @param userId     администратор, отметивший просмотр
     * @param occurredAt момент просмотра
     */
    public FilmWatchedEvent(Long filmId, Long userId, LocalDateTime occurredAt) {
        super(userId, occurredAt);
        this.filmId = filmId;
    }
}