package org.corpsman.forum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.corpsman.forum.model.Notification;
import org.corpsman.forum.model.Subscription;
import org.corpsman.forum.repository.NotificationRepository;
import org.corpsman.forum.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Сервис уведомлений и подписок.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SubscriptionRepository subscriptionRepository;

    /**
     * Создаёт уведомление.
     *
     * @param userId кому
     * @param type   тип события
     * @param text   текст
     * @param url    ссылка перехода (может быть null)
     */
    @Transactional
    public void notify(Long userId, String type, String text, String url) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setText(text);
        n.setUrl(url);
        notificationRepository.save(n);
        log.debug("Уведомление [{}] для {}: {}", type, userId, text);
    }

    /**
     * Уведомляет всех подписчиков треда, кроме автора действия.
     *
     * @param topicId тред
     * @param actorId кто произвёл действие
     * @param type    тип события
     * @param text    текст
     * @param url     ссылка
     */
    @Transactional
    public void notifyTopicSubscribers(Long topicId, Long actorId, String type, String text, String url) {
        for (Subscription s : subscriptionRepository.findByTopicId(topicId)) {
            if (!s.getSubscriberId().equals(actorId)) {
                notify(s.getSubscriberId(), type, text, url);
            }
        }
    }

    /**
     * Уведомляет всех подписчиков пользователя, кроме него самого.
     *
     * @param targetUserId чьи действия отслеживаются
     * @param actorId      кто произвёл действие
     * @param type         тип события
     * @param text         текст
     * @param url          ссылка
     */
    @Transactional
    public void notifyUserSubscribers(Long targetUserId, Long actorId, String type, String text, String url) {
        for (Subscription s : subscriptionRepository.findByTargetUserId(targetUserId)) {
            if (!s.getSubscriberId().equals(actorId)) {
                notify(s.getSubscriberId(), type, text, url);
            }
        }
    }

    /**
     * Список уведомлений пользователя.
     *
     * @param userId идентификатор пользователя
     * @return список (новые сверху)
     */
    @Transactional(readOnly = true)
    public List<Notification> listFor(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * Количество непрочитанных.
     *
     * @param userId идентификатор пользователя
     * @return количество
     */
    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    /**
     * Отмечает все уведомления пользователя прочитанными.
     *
     * @param userId идентификатор пользователя
     */
    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllRead(userId);
    }

    /**
     * Отмечает одно уведомление прочитанным (если оно принадлежит пользователю).
     *
     * @param id     идентификатор уведомления
     * @param userId пользователь
     */
    @Transactional
    public void markRead(Long id, Long userId) {
        notificationRepository.findById(id)
                .filter(n -> n.getUserId().equals(userId))
                .ifPresent(n -> {
                    n.setRead(true);
                    notificationRepository.save(n);
                });
    }
}
