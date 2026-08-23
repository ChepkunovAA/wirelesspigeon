package org.corpsman.common.event;

import lombok.Getter;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Событие изменения пожелания: CREATED / UPDATED / DELETED.
 * Получатели уведомлений определяются слушателем (подписчики владельца
 * и текущие резервировавшие).
 */
@Getter
public class WishlistChangedEvent extends BaseEvent {

    private final String action;
    private final String giftTitle;
    private final Long ownerId;
    private final List<Long> reserverUserIds;

    public WishlistChangedEvent(String action, String giftTitle, Long ownerId,
                                List<Long> reserverUserIds, LocalDateTime occurredAt) {
        super(ownerId, occurredAt);
        this.action = action;
        this.giftTitle = giftTitle;
        this.ownerId = ownerId;
        this.reserverUserIds = reserverUserIds == null ? List.of() : reserverUserIds;
    }
}
