package org.corpsman.rpg.dto;

import lombok.Data;

/**
 * Ячейки заклинаний одного уровня для отображения и редактирования.
 */
@Data
public class SpellSlotDto {

    /**
     * Уровень ячеек (1–9).
     */
    private int level;

    /**
     * Текущее (доступное) количество ячеек.
     */
    private int current;

    /**
     * Максимальное количество ячеек.
     */
    private int max;

    /**
     * Текущее количество превышает максимальное — нужен символ предупреждения.
     */
    private boolean overLimit;
}
