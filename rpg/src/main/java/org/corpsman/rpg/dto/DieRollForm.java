package org.corpsman.rpg.dto;

import lombok.Data;
import org.corpsman.rpg.model.DiceSize;

/**
 * Ввод одного броска при написании поста.
 */
@Data
public class DieRollForm {

    private DiceSize die = DiceSize.D20;

    private Integer count = 1;

    private Integer modifier = 0;

    private String comment;
}