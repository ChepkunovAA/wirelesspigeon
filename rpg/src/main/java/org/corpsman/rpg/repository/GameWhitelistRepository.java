package org.corpsman.rpg.repository;

import org.corpsman.rpg.model.GameWhitelistEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий белого списка игр.
 */
public interface GameWhitelistRepository extends JpaRepository<GameWhitelistEntry, Long> {

    List<GameWhitelistEntry> findByGameIdOrderByCreatedAtDesc(Long gameId);

    Optional<GameWhitelistEntry> findByGameIdAndUserId(Long gameId, Long userId);

    boolean existsByGameIdAndUserId(Long gameId, Long userId);

    void deleteByGameIdAndUserId(Long gameId, Long userId);

    void deleteByGameId(Long gameId);
}