package org.corpsman.rpg.repository;

import org.corpsman.rpg.model.SheetAttribute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий параметров листа персонажа.
 */
public interface SheetAttributeRepository extends JpaRepository<SheetAttribute, Long> {

    List<SheetAttribute> findBySheetIdOrderByPositionAsc(Long sheetId);

    Optional<SheetAttribute> findBySheetIdAndKey(Long sheetId, String key);

    void deleteBySheetId(Long sheetId);

    /**
     * Изменение числа монет на величину: атомарно, одним UPDATE.
     * <p>
     * Разряды монет — это «прочитал, прибавил, записал»; обычная запись через
     * JPA приводит к конфликту версии строки, если две кнопки нажаты
     * одновременно. Обновление считает на стороне базы, поэтому читать и
     * блокировать ничего не нужно.
     * <p>
     * Кнопки ± меняют сумму на единицу и не уходят в минус: если уход в минус
     * получился, сумма обрезается до нуля. Для обмена, где нужно именно
     * «хватает ли монет», используется {@link #spendMoney}.
     *
     * @return число изменённых строк (0 — параметра нет)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update SheetAttribute a
               set a.numericValue = case
                       when coalesce(a.numericValue, 0) + :delta < 0 then 0
                       else coalesce(a.numericValue, 0) + :delta
                   end
             where a.sheetId = :sheetId and a.key = :key""")
    int addMoney(@Param("sheetId") Long sheetId, @Param("key") String key, @Param("delta") int delta);

    /**
     * Списание из разряда при обмене: атомарно и только если монет хватает.
     *
     * @return 1 — списание прошло, 0 — монет не хватило
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update SheetAttribute a
               set a.numericValue = a.numericValue - :spent
             where a.sheetId = :sheetId and a.key = :key
               and a.numericValue >= :spent""")
    int spendMoney(@Param("sheetId") Long sheetId, @Param("key") String key,
                   @Param("spent") int spent);
}
