package org.corpsman.rpg.repository;

import org.corpsman.rpg.model.GameApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий заявок на участие в игре.
 */
public interface GameApplicationRepository extends JpaRepository<GameApplication, Long> {

    List<GameApplication> findByGameIdOrderByCreatedAtDesc(Long gameId);

    List<GameApplication> findByGameIdAndStatusOrderByCreatedAtDesc(Long gameId, org.corpsman.rpg.model.ApplicationStatus status);

    Optional<GameApplication> findByGameIdAndUserId(Long gameId, Long userId);

    boolean existsByGameIdAndUserId(Long gameId, Long userId);

    List<GameApplication> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId,
                                                                    org.corpsman.rpg.model.ApplicationStatus status);

    boolean existsByGameIdAndUserIdAndStatus(Long gameId, Long userId, org.corpsman.rpg.model.ApplicationStatus status);
}