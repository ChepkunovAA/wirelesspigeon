package org.corpsman.rpg.dto;

import lombok.Data;

/**
 * Ячейки заклинаний уровня для формы редактирования: текущее и максимальное
 * рядом, одним блоком. {@code currentAttrIndex}/{@code maxAttrIndex} — индексы
 * соответствующих параметров в списке атрибутов листа.
 */
@Data
public class SpellSlotEditDto {

    private int level;

    private int currentAttrIndex;

    private int maxAttrIndex;

    private Integer current;

    private Integer max;
}
