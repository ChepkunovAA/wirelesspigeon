package org.corpsman.common.event;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Событие смены статуса ролевой игры. Получатели — принятые игроки: их
 * модуль {@code rpg} и знает, и умеет определить. Статусы передаются
 * подписями, чтобы {@code common} не зависел от перечислений {@code rpg}.
 */
@Getter
public class GameStatusChangedEvent extends BaseEvent {

    private final Long gameId;
    private final String gameTitle;
    private final String oldStatusLabel;
    private final String newStatusLabel;
    private final List<Long> recipientIds;

    /**
     * Создаёт событие смены статуса игры.
     *
     * @param gameId          идентификатор игры
     * @param gameTitle       название игры
     * @param oldStatusLabel  подпись прежнего статуса
     * @param newStatusLabel  подпись нового статуса
     * @param recipientIds    получатели уведомления
     * @param actorId         инициатор смены
     * @param occurredAt      момент смены
     */
    public GameStatusChangedEvent(Long gameId, String gameTitle, String oldStatusLabel, String newStatusLabel,
                                  List<Long> recipientIds, Long actorId, LocalDateTime occurredAt) {
        super(actorId, occurredAt);
        this.gameId = gameId;
        this.gameTitle = gameTitle;
        this.oldStatusLabel = oldStatusLabel;
        this.newStatusLabel = newStatusLabel;
        this.recipientIds = recipientIds == null ? List.of() : List.copyOf(recipientIds);
    }
}
