package org.corpsman.forum.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.corpsman.common.model.BaseEntity;

/**
 * Пост форума: первое сообщение треда или комментарий.
 */
@Entity
@Table(name = "forum_posts", indexes = {
        @Index(name = "idx_posts_topic", columnList = "topic_id"),
        @Index(name = "idx_posts_parent", columnList = "parent_post_id")
})
@Getter
@Setter
public class ForumPost extends BaseEntity {

    @Column(nullable = false, length = 5000)
    private String content;

    @Column(name = "author_id")
    private Long authorId;

    @Column(name = "is_anonymous", nullable = false)
    private boolean anonymous;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Column(nullable = false)
    private int likes;

    @Column(nullable = false)
    private int dislikes;

    @Column(name = "topic_id", nullable = false)
    private Long topicId;

    @Column(name = "parent_post_id")
    private Long parentPostId;

    @Column(name = "reply_to_id")
    private Long replyToId;

    @Column(name = "is_edited", nullable = false)
    private boolean edited;
}