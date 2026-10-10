package org.corpsman.rpg.repository;

import org.corpsman.rpg.model.CustomAttribute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Репозиторий кастомных параметров игры.
 */
public interface CustomAttributeRepository extends JpaRepository<CustomAttribute, Long> {

    List<CustomAttribute> findByGameIdOrderByPositionAsc(Long gameId);
}