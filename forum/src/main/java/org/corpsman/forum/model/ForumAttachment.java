package org.corpsman.forum.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.corpsman.common.model.BaseEntity;

/**
 * Вложение комментария или стартового поста треда.
 */
@Entity
@Table(name = "forum_attachments", indexes = {
        @Index(name = "idx_attachments_post", columnList = "post_id"),
        @Index(name = "idx_attachments_topic", columnList = "topic_id")
})
@Getter
@Setter
public class ForumAttachment extends BaseEntity {

    @Column(name = "post_id")
    private Long postId;

    @Column(name = "topic_id")
    private Long topicId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "stored_name", nullable = false, unique = true, length = 255)
    private String storedName;

    @Column(name = "content_type", nullable = false, length = 255)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long size;
}