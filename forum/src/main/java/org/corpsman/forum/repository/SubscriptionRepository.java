package org.corpsman.forum.repository;

import org.corpsman.forum.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Репозиторий подписок.
 */
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    /**
     * Подписка на пользователя.
     *
     * @param subscriberId подписчик
     * @param targetUserId целевой пользователь
     * @return подписка, если есть
     */
    Optional<Subscription> findBySubscriberIdAndTargetUserId(Long subscriberId, Long targetUserId);

    /**
     * Подписка на тред.
     *
     * @param subscriberId подписчик
     * @param topicId      тред
     * @return подписка, если есть
     */
    Optional<Subscription> findBySubscriberIdAndTopicId(Long subscriberId, Long topicId);

    /**
     * Все подписчики треда.
     *
     * @param topicId идентификатор треда
     * @return список подписок
     */
    java.util.List<Subscription> findByTopicId(Long topicId);

    /**
     * Все подписчики пользователя.
     *
     * @param targetUserId идентификатор пользователя
     * @return список подписок
     */
    java.util.List<Subscription> findByTargetUserId(Long targetUserId);
}
