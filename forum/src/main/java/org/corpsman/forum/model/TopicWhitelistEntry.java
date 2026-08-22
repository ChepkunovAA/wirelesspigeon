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
 * Запись белого списка закрытого треда.
 */
@Entity
@Table(name = "forum_topic_whitelist",
        uniqueConstraints = @UniqueConstraint(columnNames = {"topic_id", "user_id"},
                name = "uk_topic_whitelist_unique"),
        indexes = {
                @Index(name = "idx_topic_whitelist_topic", columnList = "topic_id"),
                @Index(name = "idx_topic_whitelist_user", columnList = "user_id")
        })
@Getter
@Setter
public class TopicWhitelistEntry extends BaseEntity {

    @Column(name = "topic_id", nullable = false)
    private Long topicId;

    @Column(name = "user_id", nullable = false)
    private Long userId;
}
