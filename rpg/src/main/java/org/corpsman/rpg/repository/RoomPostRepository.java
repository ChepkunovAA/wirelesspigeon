package org.corpsman.rpg.repository;

import org.corpsman.rpg.model.RoomPost;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Репозиторий постов комнат.
 */
public interface RoomPostRepository extends JpaRepository<RoomPost, Long> {

    List<RoomPost> findByRoomIdOrderByCreatedAtAsc(Long roomId);
}