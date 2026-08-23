package org.corpsman.forum.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.corpsman.common.model.BaseEntity;

/**
 * Подписка пользователя на действия другого пользователя или тред.
 */
@Entity
@Table(name = "subscriptions", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"subscriber_id", "target_user_id"}, name = "uk_sub_user_unique"),
        @UniqueConstraint(columnNames = {"subscriber_id", "topic_id"}, name = "uk_sub_topic_unique")
})
@Getter
@Setter
public class Subscription extends BaseEntity {

    @Column(name = "subscriber_id", nullable = false)
    private Long subscriberId;

    /** Подписка на действия пользователя (null при подписке на тред). */
    @Column(name = "target_user_id")
    private Long targetUserId;

    /** Подписка на тред (null при подписке на пользователя). */
    @Column(name = "topic_id")
    private Long topicId;
}
