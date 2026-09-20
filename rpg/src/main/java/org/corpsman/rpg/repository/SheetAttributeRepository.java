package org.corpsman.rpg.repository;

import org.corpsman.rpg.model.SheetAttribute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Репозиторий параметров листа персонажа.
 */
public interface SheetAttributeRepository extends JpaRepository<SheetAttribute, Long> {

    List<SheetAttribute> findBySheetIdOrderByPositionAsc(Long sheetId);

    void deleteBySheetId(Long sheetId);
}