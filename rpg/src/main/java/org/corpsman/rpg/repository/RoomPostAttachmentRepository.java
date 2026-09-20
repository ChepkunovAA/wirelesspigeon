package org.corpsman.rpg.repository;

import org.corpsman.rpg.model.RoomPostAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Репозиторий вложений постов комнаты.
 */
public interface RoomPostAttachmentRepository extends JpaRepository<RoomPostAttachment, Long> {

    List<RoomPostAttachment> findByPostIdOrderByIdAsc(Long postId);

    List<RoomPostAttachment> findByPostIdInOrderByIdAsc(List<Long> postIds);
}