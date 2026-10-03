package org.corpsman.rpg.repository;

import org.corpsman.rpg.model.GameSystemDefinition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий определений ролевых систем (библиотека систем).
 */
public interface GameSystemDefinitionRepository extends JpaRepository<GameSystemDefinition, Long> {

    List<GameSystemDefinition> findByIsPublicTrueOrderByNameAsc();

    List<GameSystemDefinition> findByAuthorIdOrderByCreatedAtDesc(Long authorId);

    Optional<GameSystemDefinition> findByNameIgnoreCase(String name);

    @Query("SELECT g FROM GameSystemDefinition g WHERE g.isPublic = true OR g.authorId = :userId")
    List<GameSystemDefinition> findAvailableForUser(@Param("userId") Long userId);

    @Query("SELECT g FROM GameSystemDefinition g WHERE g.isPublic = true OR g.authorId = :userId")
    Page<GameSystemDefinition> findAvailableForUser(@Param("userId") Long userId, org.springframework.data.domain.Pageable pageable);

    boolean existsByNameIgnoreCase(String name);
}