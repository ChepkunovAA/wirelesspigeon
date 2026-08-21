package org.corpsman.common.event;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Событие регистрации нового пользователя.
 */
@Getter
public class UserRegisteredEvent extends BaseEvent {

    private final String username;

    /**
     * Создаёт событие регистрации пользователя.
     *
     * @param userId     идентификатор созданного пользователя
     * @param username   имя пользователя
     * @param occurredAt момент регистрации
     */
    public UserRegisteredEvent(Long userId, String username, LocalDateTime occurredAt) {
        super(userId, occurredAt);
        this.username = username;
    }
}