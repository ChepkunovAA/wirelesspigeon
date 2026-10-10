package org.corpsman.rpg.dto;

import lombok.Data;
import org.corpsman.rpg.model.DiceSize;

/**
 * Ввод сохранённой комбинации броска.
 */
@Data
public class SavedRollForm {

    private DiceSize die = DiceSize.D20;

    private Integer count = 1;

    private Integer modifier = 0;

    private String comment;
}