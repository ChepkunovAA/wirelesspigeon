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
 * Бан пользователя в конкретном треде.
 */
@Entity
@Table(name = "topic_bans",
        uniqueConstraints = @UniqueConstraint(name = "uk_topic_ban", columnNames = {"topic_id", "user_id"}),
        indexes = @Index(name = "idx_bans_topic", columnList = "topic_id"))
@Getter
@Setter
public class TopicBan extends BaseEntity {

    @Column(name = "topic_id", nullable = false)
    private Long topicId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "banned_by", nullable = false)
    private Long bannedBy;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(length = 500)
    private String reason;
}