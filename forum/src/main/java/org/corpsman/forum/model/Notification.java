package org.corpsman.forum.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.corpsman.common.model.BaseEntity;


/**
 * Уведомление пользователя.
 */
@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notifications_user", columnList = "user_id, is_read")
})
@Getter
@Setter
public class Notification extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Тип события: REPLY / TOPIC_COMMENT / FILM_SELECTED / RESERVATION_CANCELLED / SUBSCRIPTION. */
    @Column(nullable = false, length = 40)
    private String type;

    @Column(nullable = false, length = 500)
    private String text;

    /** Ссылка перехода (может быть null). */
    @Column(length = 300)
    private String url;

    @Column(name = "is_read", nullable = false)
    private boolean read;
}
