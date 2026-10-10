package org.corpsman.common.event;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Событие нового поста в комнате ролевой игры. Получатели уже определены
 * модулем {@code rpg}: только они видят комнату и могут в ней участвовать.
 */
@Getter
public class GameRoomPostCreatedEvent extends BaseEvent {

    private final Long gameId;
    private final String gameTitle;
    private final Long roomId;
    private final String roomTitle;
    private final String authorName;
    private final boolean authorIsMaster;
    private final List<Long> recipientIds;

    /**
     * Создаёт событие нового поста в комнате.
     *
     * @param gameId        идентификатор игры
     * @param gameTitle     название игры
     * @param roomId        идентификатор комнаты
     * @param roomTitle     название комнаты
     * @param authorName    имя автора поста
     * @param authorIsMaster ведущий ли автор
     * @param recipientIds  получатели уведомления
     * @param authorId      идентификатор автора
     * @param occurredAt    момент создания поста
     */
    public GameRoomPostCreatedEvent(Long gameId, String gameTitle, Long roomId, String roomTitle,
                                    String authorName, boolean authorIsMaster, List<Long> recipientIds,
                                    Long authorId, LocalDateTime occurredAt) {
        super(authorId, occurredAt);
        this.gameId = gameId;
        this.gameTitle = gameTitle;
        this.roomId = roomId;
        this.roomTitle = roomTitle;
        this.authorName = authorName;
        this.authorIsMaster = authorIsMaster;
        this.recipientIds = recipientIds == null ? List.of() : List.copyOf(recipientIds);
    }
}
