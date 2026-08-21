package org.corpsman.common.event;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Событие выбора фильма для следующего просмотра.
 */
@Getter
public class FilmSelectedEvent extends BaseEvent {

    private final Long filmId;
    private final String filmTitle;
    private final Long filmOwnerId;

    /**
     * Создаёт событие выбора фильма.
     *
     * @param filmId      идентификатор выбранного фильма
     * @param filmTitle   название фильма
     * @param filmOwnerId пользователь, предложивший фильм
     * @param userId      администратор, выполнивший выбор
     * @param occurredAt  момент выбора
     */
    public FilmSelectedEvent(Long filmId, String filmTitle, Long filmOwnerId, Long userId, LocalDateTime occurredAt) {
        super(userId, occurredAt);
        this.filmId = filmId;
        this.filmTitle = filmTitle;
        this.filmOwnerId = filmOwnerId;
    }
}