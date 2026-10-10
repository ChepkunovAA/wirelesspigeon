package org.corpsman.rpg.dto;

import lombok.Data;
import org.corpsman.rpg.model.DiceSize;

import java.util.List;

/**
 * Бросок костей, прикреплённый к посту.
 */
@Data
public class DiceRollDto {

    private DiceSize die;

    private int count;

    private int modifier;

    private String comment;

    private List<Integer> results;

    private int total;
}