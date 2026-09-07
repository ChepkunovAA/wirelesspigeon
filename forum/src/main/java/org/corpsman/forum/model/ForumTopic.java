package org.corpsman.forum.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.corpsman.common.model.BaseEntity;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Тред форума.
 */
@Entity
@Table(name = "forum_topics", indexes = {
        @Index(name = "idx_topics_last_activity", columnList = "last_activity_at"),
        @Index(name = "idx_topics_author", columnList = "author_id")
})
@Getter
@Setter
public class ForumTopic extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 1000000)
    private String content;

    @Column(name = "author_id")
    private Long authorId;

    @Column(name = "is_anonymous", nullable = false)
    private boolean anonymous;

    @Column(name = "is_frozen", nullable = false)
    private boolean frozen;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    private TopicVisibility visibility = TopicVisibility.OPEN;

    /**
     * Только для чтения: писать может только владелец треда.
     */
    @Column(name = "read_only", nullable = false)
    private boolean readOnly;

    @Column(nullable = false)
    private int likes;

    @Column(nullable = false)
    private int dislikes;

    @Column(name = "last_activity_at", nullable = false)
    private LocalDateTime lastActivityAt;

    /**
     * При создании последняя активность равна моменту создания треда:
     * {@code lastActivityAt == createdAt} (без микро-сдвига, возникавшего,
     * когда сервис задавал {@code lastActivityAt} до срабатывания
     * {@code @PrePersist} базовой сущности).
     */
    @PrePersist
    @Override
    protected void onCreate() {
        super.onCreate();
        lastActivityAt = getCreatedAt();
    }

    @ManyToMany
    @JoinTable(name = "topic_tags",
            joinColumns = @JoinColumn(name = "topic_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<ForumTag> tags = new HashSet<>();
}