package org.corpsman.rpg.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Группа заклинаний одного уровня.
 */
@Data
public class SpellGroupDto {

    private int level;

    private List<SpellListItemDto> items = new ArrayList<>();
}
