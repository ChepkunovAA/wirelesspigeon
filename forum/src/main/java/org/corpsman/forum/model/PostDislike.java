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
 * Дизлайк комментария на форуме.
 */
@Entity
@Table(name = "post_dislikes",
        uniqueConstraints = @UniqueConstraint(name = "uk_post_dislike", columnNames = {"post_id", "user_id"}),
        indexes = @Index(name = "idx_dislikes_post", columnList = "post_id"))
@Getter
@Setter
public class PostDislike extends BaseEntity {

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "user_id", nullable = false)
    private Long userId;
}