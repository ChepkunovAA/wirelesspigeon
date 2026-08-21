package org.corpsman.forum.repository;

import org.corpsman.forum.model.TopicDislike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Репозиторий дизлайков первого поста треда.
 */
public interface TopicDislikeRepository extends JpaRepository<TopicDislike, Long> {

    boolean existsByTopicIdAndUserId(Long topicId, Long userId);

    Optional<TopicDislike> findByTopicIdAndUserId(Long topicId, Long userId);

    void deleteByTopicIdAndUserId(Long topicId, Long userId);
}