package org.corpsman.auth.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Настройки пользователя клуба.
 */
@Entity
@Table(name = "user_settings")
@Getter
@Setter
public class UserSettings {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "default_thread_anonymous", nullable = false)
    private boolean defaultThreadAnonymous;

    @Column(name = "default_comment_anonymous", nullable = false)
    private boolean defaultCommentAnonymous;

    /** Видимость новых тредов по умолчанию: PUBLIC/OPEN/CLOSED. */
    @Column(name = "default_visibility", nullable = false, length = 20)
    private String defaultVisibility = "OPEN";

    /** Новые треды по умолчанию только для чтения. */
    @Column(name = "default_read_only", nullable = false)
    private boolean defaultReadOnly;

    @Column(name = "show_public_posts", nullable = false)
    private boolean showPublicPosts = true;
}
