package org.corpsman.common.event;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Событие резервирования подарка.
 */
@Getter
public class GiftReservedEvent extends BaseEvent {

    private final Long giftId;
    private final Long reservedByUserId;

    /**
     * Создаёт событие резервирования подарка.
     *
     * @param giftId          идентификатор зарезервированного подарка
     * @param reservedByUserId пользователь, зарезервировавший подарок
     * @param occurredAt      момент резервирования
     */
    public GiftReservedEvent(Long giftId, Long reservedByUserId, LocalDateTime occurredAt) {
        super(reservedByUserId, occurredAt);
        this.giftId = giftId;
        this.reservedByUserId = reservedByUserId;
    }
}