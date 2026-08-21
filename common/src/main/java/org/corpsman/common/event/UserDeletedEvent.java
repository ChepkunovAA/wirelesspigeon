package org.corpsman.common.event;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Событие удаления пользователя.
 */
@Getter
public class UserDeletedEvent extends BaseEvent {

    private final String username;

    /**
     * Создаёт событие удаления пользователя.
     *
     * @param userId     идентификатор удаляемого пользователя
     * @param username   имя удаляемого пользователя
     * @param occurredAt момент удаления
     */
    public UserDeletedEvent(Long userId, String username, LocalDateTime occurredAt) {
        super(userId, occurredAt);
        this.username = username;
    }
}