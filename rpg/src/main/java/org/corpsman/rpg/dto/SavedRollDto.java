package org.corpsman.rpg.dto;

import lombok.Data;
import org.corpsman.rpg.model.DiceSize;

/**
 * Сохранённая комбинация броска.
 */
@Data
public class SavedRollDto {

    private Long id;

    private DiceSize die;

    private int count;

    private int modifier;

    private String comment;
}