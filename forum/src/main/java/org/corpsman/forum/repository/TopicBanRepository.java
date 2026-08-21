package org.corpsman.forum.repository;

import org.corpsman.forum.model.TopicBan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий банов в тредах.
 */
public interface TopicBanRepository extends JpaRepository<TopicBan, Long> {

    List<TopicBan> findByTopicIdOrderByCreatedAtDesc(Long topicId);

    boolean existsByTopicIdAndUserId(Long topicId, Long userId);

    Optional<TopicBan> findByTopicIdAndUserId(Long topicId, Long userId);

    void deleteByTopicIdAndUserId(Long topicId, Long userId);
}