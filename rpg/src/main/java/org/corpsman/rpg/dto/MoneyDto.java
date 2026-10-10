package org.corpsman.rpg.dto;

/**
 * Сумма монет на листе: три вида с фиксированным курсом 1:10.
 */
public record MoneyDto(int gold, int silver, int copper) {
}
