package org.corpsman.forum.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.corpsman.common.model.BaseEntity;

/**
 * Лайк первого поста (треда) на форуме.
 */
@Entity
@Table(name = "topic_likes",
        uniqueConstraints = @UniqueConstraint(name = "uk_topic_like", columnNames = {"topic_id", "user_id"}),
        indexes = @Index(name = "idx_topic_likes_topic", columnList = "topic_id"))
@Getter
@Setter
public class TopicLike extends BaseEntity {

    @Column(name = "topic_id", nullable = false)
    private Long topicId;

    @Column(name = "user_id", nullable = false)
    private Long userId;
}