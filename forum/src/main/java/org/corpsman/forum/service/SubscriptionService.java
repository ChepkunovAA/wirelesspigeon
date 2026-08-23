package org.corpsman.forum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.corpsman.common.exception.BusinessException;
import org.corpsman.common.exception.ErrorCode;
import org.corpsman.forum.model.Subscription;
import org.corpsman.forum.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сервис подписок на пользователей и треды.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;

    /**
     * Переключает подписку на пользователя (подписаться/отписаться).
     *
     * @param subscriberId подписчик
     * @param targetUserId целевой пользователь
     * @return true — теперь подписан, false — отписан
     */
    @Transactional
    public boolean toggleUserSubscription(Long subscriberId, Long targetUserId) {
        if (subscriberId.equals(targetUserId)) {
            throw new BusinessException(ErrorCode.SUBSCRIPTION_SELF);
        }
        var existing = subscriptionRepository
                .findBySubscriberIdAndTargetUserId(subscriberId, targetUserId);
        if (existing.isPresent()) {
            subscriptionRepository.delete(existing.get());
            return false;
        }
        Subscription s = new Subscription();
        s.setSubscriberId(subscriberId);
        s.setTargetUserId(targetUserId);
        subscriptionRepository.save(s);
        return true;
    }

    /**
     * Переключает подписку на тред.
     *
     * @param subscriberId подписчик
     * @param topicId      тред
     * @return true — теперь подписан, false — отписан
     */
    @Transactional
    public boolean toggleTopicSubscription(Long subscriberId, Long topicId) {
        var existing = subscriptionRepository.findBySubscriberIdAndTopicId(subscriberId, topicId);
        if (existing.isPresent()) {
            subscriptionRepository.delete(existing.get());
            return false;
        }
        Subscription s = new Subscription();
        s.setSubscriberId(subscriberId);
        s.setTopicId(topicId);
        subscriptionRepository.save(s);
        return true;
    }

    /**
     * Подписан ли пользователь на тред.
     *
     * @param userId  пользователь
     * @param topicId тред
     * @return true, если подписан
     */
    @Transactional(readOnly = true)
    public boolean isSubscribedToTopic(Long userId, Long topicId) {
        return subscriptionRepository.findBySubscriberIdAndTopicId(userId, topicId).isPresent();
    }

    /**
     * Подписан ли пользователь на другого пользователя.
     *
     * @param userId   подписчик
     * @param targetId целевой пользователь
     * @return true, если подписан
     */
    @Transactional(readOnly = true)
    public boolean isSubscribedToUser(Long userId, Long targetId) {
        return subscriptionRepository.findBySubscriberIdAndTargetUserId(userId, targetId).isPresent();
    }
}
