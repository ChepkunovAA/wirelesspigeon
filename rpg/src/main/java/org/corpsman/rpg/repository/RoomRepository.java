package org.corpsman.rpg.repository;

import org.corpsman.rpg.model.Room;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Репозиторий комнат игры.
 */
public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findByGameIdOrderByCreatedAtDesc(Long gameId);
}