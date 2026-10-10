package org.corpsman.common.event;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Событие по заявке игрока и по составу участников: подана, отозвана,
 * рассмотрена, игрок ушёл или был удалён. Получатели уже определены модулем
 * {@code rpg}: у него есть правила доступа к игре и комнатам.
 */
@Getter
public class GameApplicationEvent extends BaseEvent {

    /** Заявка подана. */
    public static final String SUBMITTED = "SUBMITTED";
    /** Заявка отозвана игроком. */
    public static final String WITHDRAWN = "WITHDRAWN";
    /** Заявка принята. */
    public static final String ACCEPTED = "ACCEPTED";
    /** Заявка отклонена. */
    public static final String REJECTED = "REJECTED";
    /** Игрок покинул игру сам. */
    public static final String LEFT = "LEFT";
    /** Ведущий удалил игрока из игры. */
    public static final String REMOVED = "REMOVED";

    private final Long gameId;
    private final String gameTitle;
    private final Long applicationId;
    private final Long playerId;
    private final String playerName;
    private final String action;
    private final boolean accepted;
    private final List<Long> recipientIds;

    /**
     * Создаёт событие по заявке или составу участников.
     *
     * @param gameId        идентификатор игры
     * @param gameTitle     название игры
     * @param applicationId идентификатор заявки
     * @param playerId      идентификатор игрока
     * @param playerName    имя игрока
     * @param action        одно из действий {@code SUBMITTED}, {@code WITHDRAWN},
     *                      {@code ACCEPTED}, {@code REJECTED}, {@code LEFT}, {@code REMOVED}
     * @param accepted      результат рассмотрения заявки
     * @param recipientIds  получатели уведомления
     * @param actorId       инициатор действия
     * @param occurredAt    момент действия
     */
    public GameApplicationEvent(Long gameId, String gameTitle, Long applicationId, Long playerId,
                                String playerName, String action, boolean accepted, List<Long> recipientIds,
                                Long actorId, LocalDateTime occurredAt) {
        super(actorId, occurredAt);
        this.gameId = gameId;
        this.gameTitle = gameTitle;
        this.applicationId = applicationId;
        this.playerId = playerId;
        this.playerName = playerName;
        this.action = action;
        this.accepted = accepted;
        this.recipientIds = recipientIds == null ? List.of() : List.copyOf(recipientIds);
    }
}
