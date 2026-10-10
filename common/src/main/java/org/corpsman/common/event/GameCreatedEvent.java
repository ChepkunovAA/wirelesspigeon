package org.corpsman.common.event;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Событие создания ролевой игры. Получатели определяются подписками на
 * автора: уведомляются те, кто на него подписан.
 */
@Getter
public class GameCreatedEvent extends BaseEvent {

    private final Long gameId;
    private final String gameTitle;
    private final String authorName;

    /**
     * Создаёт событие создания игры.
     *
     * @param gameId     идентификатор игры
     * @param gameTitle  название игры
     * @param authorName имя ведущего
     * @param authorId   идентификатор ведущего
     * @param occurredAt момент создания
     */
    public GameCreatedEvent(Long gameId, String gameTitle, String authorName, Long authorId,
                            LocalDateTime occurredAt) {
        super(authorId, occurredAt);
        this.gameId = gameId;
        this.gameTitle = gameTitle;
        this.authorName = authorName;
    }
}
