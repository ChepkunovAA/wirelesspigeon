package org.corpsman.common.event;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Базовое доменное событие.
 * <p>
 * Все события несут идентификатор инициатора и момент возникновения.
 */
@Getter
public abstract class BaseEvent {

    private final Long userId;
    private final LocalDateTime occurredAt;

    protected BaseEvent(Long userId, LocalDateTime occurredAt) {
        this.userId = userId;
        this.occurredAt = occurredAt;
    }
}