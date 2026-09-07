package org.corpsman.forum.repository;

import org.corpsman.forum.model.ForumAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Репозиторий вложений форума.
 */
public interface ForumAttachmentRepository extends JpaRepository<ForumAttachment, Long> {

    List<ForumAttachment> findByPostIdOrderByIdAsc(Long postId);

    List<ForumAttachment> findByTopicIdOrderByIdAsc(Long topicId);

    List<ForumAttachment> findByPostIdIn(List<Long> postIds);

    @Modifying
    @Query("DELETE FROM ForumAttachment a WHERE a.postId = :postId")
    void deleteByPostId(@Param("postId") Long postId);
}