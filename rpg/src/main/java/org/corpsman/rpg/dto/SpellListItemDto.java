package org.corpsman.rpg.dto;

import lombok.Data;

/**
 * Заклинание для отображения с группировкой по уровню. {@code index} — позиция
 * элемента в исходном списке листа, нужна для удаления.
 */
@Data
public class SpellListItemDto {

    private int index;

    private String header;

    private String description;

    private int level;
}
