package org.corpsman.rpg.repository;

import org.corpsman.rpg.model.DiceRoll;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Репозиторий бросков костей.
 */
public interface DiceRollRepository extends JpaRepository<DiceRoll, Long> {

    List<DiceRoll> findByPostIdOrderByIdAsc(Long postId);
}