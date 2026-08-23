package org.corpsman.forum.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.corpsman.auth.model.User;
import org.corpsman.auth.repository.UserRepository;
import org.corpsman.common.event.FilmSelectedEvent;
import org.corpsman.common.event.WishlistChangedEvent;
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
