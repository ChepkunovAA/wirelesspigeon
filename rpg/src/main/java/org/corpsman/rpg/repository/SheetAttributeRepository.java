package org.corpsman.rpg.repository;

import org.corpsman.rpg.model.SheetAttribute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий параметров листа персонажа.
 */
public interface SheetAttributeRepository extends JpaRepository<SheetAttribute, Long> {

    List<SheetAttribute> findBySheetIdOrderByPositionAsc(Long sheetId);

    Optional<SheetAttribute> findBySheetIdAndKey(Long sheetId, String key);

    void deleteBySheetId(Long sheetId);
}