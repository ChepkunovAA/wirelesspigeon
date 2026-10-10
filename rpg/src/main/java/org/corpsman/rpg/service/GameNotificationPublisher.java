package org.corpsman.rpg.service;

import lombok.RequiredArgsConstructor;
import org.corpsman.auth.repository.UserRepository;
import org.corpsman.common.event.GameApplicationEvent;
import org.corpsman.common.event.GameCreatedEvent;
import org.corpsman.common.event.GameRoomPostCreatedEvent;
import org.corpsman.common.event.GameStatusChangedEvent;
import org.corpsman.rpg.model.ApplicationStatus;
import org.corpsman.rpg.model.Game;
import org.corpsman.rpg.model.GameApplication;
import org.corpsman.rpg.model.GameStatus;
import org.corpsman.rpg.model.Room;
import org.corpsman.rpg.repository.GameApplicationRepository;
import org.corpsman.rpg.repository.RoomMemberRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Публикует события ролевых игр. Получатели уведомлений определяются здесь:
 * правила доступа к игре и комнатам живут в модуле {@code rpg}, а уведомления
 * создаёт слушатель в {@code forum}, поэтому зависимости остаются односторонними.
 */
@Component
@RequiredArgsConstructor
public class GameNotificationPublisher {

    private final ApplicationEventPublisher events;
    private final GameApplicationRepository applicationRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserRepository userRepository;

    /**
     * Игра создана: уведомляются подписчики ведущего (их находит слушатель).
     */
    public void gameCreated(Game game) {
        events.publishEvent(new GameCreatedEvent(game.getId(), game.getTitle(),
                userName(game.getMasterId()), game.getMasterId(), LocalDateTime.now()));
    }

    /**
     * Игрок подал заявку: уведомляется ведущий.
     */
    public void applicationSubmitted(Game game, GameApplication application) {
        publishApplication(game, application, GameApplicationEvent.SUBMITTED, true, List.of(game.getMasterId()),
                application.getUserId());
    }

    /**
     * Игрок отозвал заявку: уведомляется ведущий.
     */
    public void applicationWithdrawn(Game game, GameApplication application) {
        publishApplication(game, application, GameApplicationEvent.WITHDRAWN, true,
                List.of(game.getMasterId()), application.getUserId());
    }

    /**
     * Заявка рассмотрена: уведомляется игрок.
     */
    public void applicationReviewed(Game game, GameApplication application, boolean accepted) {
        publishApplication(game, application, accepted ? GameApplicationEvent.ACCEPTED
                : GameApplicationEvent.REJECTED, accepted, List.of(application.getUserId()),
                game.getMasterId());
    }

    /**
     * Игрок покинул игру: уведомляется ведущий.
     */
    public void playerLeft(Game game, GameApplication application) {
        publishApplication(game, application, GameApplicationEvent.LEFT, true,
                List.of(game.getMasterId()), application.getUserId());
    }

    /**
     * Ведущий удалил игрока: уведомляется игрок.
     */
    public void playerRemoved(Game game, GameApplication application) {
        publishApplication(game, application, GameApplicationEvent.REMOVED, true,
                List.of(application.getUserId()), game.getMasterId());
    }

    /**
     * Игра изменила статус: уведомляются принятые игроки.
     */
    public void statusChanged(Game game, GameStatus oldStatus, GameStatus newStatus) {
        List<Long> recipients = acceptedPlayerIds(game.getId());
        if (recipients.isEmpty()) {
            return;
        }
        events.publishEvent(new GameStatusChangedEvent(game.getId(), game.getTitle(),
                oldStatus == null ? null : oldStatus.label(), newStatus == null ? null : newStatus.label(),
                recipients, game.getMasterId(), LocalDateTime.now()));
    }

    /**
     * Новый пост в комнате: уведомляются участники, которые видят комнату, кроме
     * автора. Пост игрока дополнительно уведомляет ведущего.
     */
    public void roomPostCreated(Game game, Room room, Long authorId, boolean authorIsMaster) {
        Set<Long> recipients = new LinkedHashSet<>();
        if (!authorIsMaster) {
            recipients.add(game.getMasterId());
        }
        Set<Long> roomMembers = room.isPrivateRoom() ? roomMemberIds(room.getId()) : Set.of();
        for (Long playerId : acceptedPlayerIds(game.getId())) {
            if (playerId.equals(authorId)) {
                continue;
            }
            if (!room.isPrivateRoom() || roomMembers.contains(playerId)) {
                recipients.add(playerId);
            }
        }
        recipients.remove(null);
        if (recipients.isEmpty()) {
            return;
        }
        events.publishEvent(new GameRoomPostCreatedEvent(game.getId(), game.getTitle(), room.getId(),
                room.getTitle(), userName(authorId), authorIsMaster, List.copyOf(recipients), authorId,
                LocalDateTime.now()));
    }

    private void publishApplication(Game game, GameApplication application, String action, boolean accepted,
                                     List<Long> recipients, Long actorId) {
        events.publishEvent(new GameApplicationEvent(game.getId(), game.getTitle(), application.getId(),
                application.getUserId(), userName(application.getUserId()), action, accepted, recipients,
                actorId, LocalDateTime.now()));
    }

    private List<Long> acceptedPlayerIds(Long gameId) {
        return applicationRepository.findByGameIdAndStatusOrderByCreatedAtDesc(gameId,
                        ApplicationStatus.ACCEPTED).stream()
                .map(GameApplication::getUserId)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private Set<Long> roomMemberIds(Long roomId) {
        Set<Long> members = new LinkedHashSet<>();
        for (var member : roomMemberRepository.findByRoomId(roomId)) {
            members.add(member.getUserId());
        }
        return members;
    }

    private String userName(Long userId) {
        if (userId == null) {
            return "неизвестный пользователь";
        }
        return userRepository.findById(userId)
                .map(u -> u.getUsername())
                .orElse("неизвестный пользователь");
    }
}
