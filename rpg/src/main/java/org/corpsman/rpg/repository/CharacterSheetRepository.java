package org.corpsman.rpg.repository;

import org.corpsman.rpg.model.CharacterSheet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Репозиторий листов персонажей.
 */
public interface CharacterSheetRepository extends JpaRepository<CharacterSheet, Long> {

    List<CharacterSheet> findByGameIdOrderByCreatedAtDesc(Long gameId);

    List<CharacterSheet> findByGameIdAndNpcOrderByCreatedAtDesc(Long gameId, boolean npc);

    List<CharacterSheet> findByOwnerIdAndGameIdOrderByCreatedAtDesc(Long ownerId, Long gameId);

    List<CharacterSheet> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);
}