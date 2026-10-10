package org.corpsman.forum.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.corpsman.auth.model.User;
import org.corpsman.auth.repository.UserRepository;
import org.corpsman.common.event.FilmSelectedEvent;
import org.corpsman.common.event.GameApplicationEvent;
import org.corpsman.common.event.GameCreatedEvent;
import org.corpsman.common.event.GameRoomPostCreatedEvent;
import org.corpsman.common.event.GameStatusChangedEvent;
import org.corpsman.common.event.WishlistChangedEvent;
import org.corpsman.forum.model.Subscription;
import org.corpsman.forum.repository.SubscriptionRepository;
import org.corpsman.forum.service.NotificationService;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Слушатель доменных событий, порождающий уведомления.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationsEventListener {

    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;

    /**
     * Уведомляет всех активных пользователей о выборе фильма.
     */
    @Async("corpsmanTaskExecutor")
    @EventListener
    public void onFilmSelected(FilmSelectedEvent event) {
        String text = "Выбран фильм для просмотра: «" + event.getFilmTitle() + "»";
        for (User u : userRepository.findAll()) {
            if (u.isActive() && !u.getId().equals(event.getFilmOwnerId())) {
                notificationService.notify(u.getId(), "FILM_SELECTED", text, "/cinema");
            }
        }
    }

    /**
     * Новая ролевая игра: уведомляются подписчики ведущего, кроме него самого.
     */
    @Async("corpsmanTaskExecutor")
    @EventListener
    public void onGameCreated(GameCreatedEvent event) {
        String text = "Пользователь " + event.getAuthorName() + " создал игру «" + event.getGameTitle() + "»";
        for (Subscription s : subscriptionRepository.findByTargetUserId(event.getUserId())) {
            if (!s.getSubscriberId().equals(event.getUserId())) {
                notificationService.notify(s.getSubscriberId(), "RPG_GAME_CREATED", text,
                        "/rpg/games/" + event.getGameId());
            }
        }
    }

    /**
     * Заявка и состав участников: получатели уже отобраны модулем rpg.
     */
    @Async("corpsmanTaskExecutor")
    @EventListener
    public void onGameApplication(GameApplicationEvent event) {
        String game = "«" + event.getGameTitle() + "»";
        String player = event.getPlayerName();
        String url = "/rpg/games/" + event.getGameId();
        switch (event.getAction()) {
            case GameApplicationEvent.SUBMITTED -> {
                notificationService.notify(event.getRecipientIds(), "RPG_APPLICATION_SUBMITTED",
                        player + " подал заявку в игру " + game, url);
            }
            case GameApplicationEvent.WITHDRAWN -> {
                notificationService.notify(event.getRecipientIds(), "RPG_APPLICATION_WITHDRAWN",
                        player + " отозвал заявку из игры " + game, url);
            }
            case GameApplicationEvent.ACCEPTED -> {
                notificationService.notify(event.getRecipientIds(), "RPG_APPLICATION_ACCEPTED",
                        "Ваша заявка в игру " + game + " принята", url);
            }
            case GameApplicationEvent.REJECTED -> {
                notificationService.notify(event.getRecipientIds(), "RPG_APPLICATION_REJECTED",
                        "Ваша заявка в игру " + game + " отклонена", url);
            }
            case GameApplicationEvent.LEFT -> {
                notificationService.notify(event.getRecipientIds(), "RPG_GAME_PLAYER_LEFT",
                        player + " покинул игру " + game, url);
            }
            case GameApplicationEvent.REMOVED -> {
                notificationService.notify(event.getRecipientIds(), "RPG_GAME_PLAYER_REMOVED",
                        "Вы были удалены из игры " + game, url);
            }
            default -> log.debug("Неизвестное действие с заявкой: {}", event.getAction());
        }
    }

    /**
     * Смена статуса игры: уведомляются принятые игроки.
     */
    @Async("corpsmanTaskExecutor")
    @EventListener
    public void onGameStatusChanged(GameStatusChangedEvent event) {
        String text = "Игра «" + event.getGameTitle() + "» изменила статус: "
                + event.getOldStatusLabel() + " → " + event.getNewStatusLabel();
        notificationService.notify(event.getRecipientIds(), "RPG_GAME_STATUS_CHANGED", text,
                "/rpg/games/" + event.getGameId());
    }

    /**
     * Новый пост в комнате игры: уведомляются участники, которым комната доступна.
     */
    @Async("corpsmanTaskExecutor")
    @EventListener
    public void onRoomPostCreated(GameRoomPostCreatedEvent event) {
        String text = "Новый пост в комнате «" + event.getRoomTitle() + "» игры «" + event.getGameTitle()
                + "» от " + event.getAuthorName();
        notificationService.notify(event.getRecipientIds(), "RPG_ROOM_POST", text,
                "/rpg/games/" + event.getGameId() + "/rooms/" + event.getRoomId());
    }

    /**
     * Изменение пожелания: подписчикам владельца + резервировавшим.
     * Владелец уведомлений о чужих действиях с его списком не получает.
     */
    @Async("corpsmanTaskExecutor")
    @EventListener
    public void onWishlistChanged(WishlistChangedEvent event) {
        Set<Long> recipients = new HashSet<>();
        for (var s : subscriptionRepository.findByTargetUserId(event.getOwnerId())) {
            recipients.add(s.getSubscriberId());
        }
        recipients.addAll(event.getReserverUserIds());
        recipients.remove(event.getOwnerId());

        String text = switch (event.getAction()) {
            case "CREATED" -> "Пользователь добавил новое пожелание «" + event.getGiftTitle() + "»";
            case "UPDATED" -> "Пожелание «" + event.getGiftTitle() + "» было отредактировано";
            default -> "Пожелание «" + event.getGiftTitle() + "» удалено владельцем";
        };
        for (Long recipient : recipients) {
            notificationService.notify(recipient, "WISHLIST_" + event.getAction(), text, "/gifts");
        }
        log.debug("Wishlist {}: уведомлено {} получателей", event.getAction(), recipients.size());
    }
}
