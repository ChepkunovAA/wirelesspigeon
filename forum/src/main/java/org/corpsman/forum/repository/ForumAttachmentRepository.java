package org.corpsman.forum.repository;

import org.corpsman.forum.model.ForumAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Репозиторий вложений форума.
 */
public interface ForumAttachmentRepository extends JpaRepository<ForumAttachment, Long> {

    List<ForumAttachment> findByPostIdOrderByIdAsc(Long postId);

    List<ForumAttachment> findByTopicIdOrderByIdAsc(Long topicId);

    List<ForumAttachment> findByPostIdIn(List<Long> postIds);
}