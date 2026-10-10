package org.corpsman.rpg.dto;

import lombok.Data;

/**
 * Модификатор для всплывающего списка автоматической подстановки в проверки.
 */
@Data
public class CharacterModifierDto {

    private String key;

    private String label;

    private int value;
}