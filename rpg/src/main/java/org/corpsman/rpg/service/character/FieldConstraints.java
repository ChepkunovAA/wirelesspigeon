package org.corpsman.rpg.service.character;

import java.util.List;

/**
 * Ограничения значения параметра, заданные автором кастомной системы.
 *
 * @param defaultValue        значение по умолчанию для числового параметра
 * @param defaultText         значение по умолчанию для текстового параметра
 * @param min                нижняя граница числового параметра
 * @param max                верхняя граница числового параметра
 * @param step               шаг стрелок числового параметра
 * @param options            предустановленные значения текстового параметра
 * @param manualInputAllowed разрешён ли ручной ввод
 */
public record FieldConstraints(Integer defaultValue, String defaultText, Integer min, Integer max,
                               Integer step, List<String> options, boolean manualInputAllowed) {

    /**
     * Пустые ограничения: значение не ограничено, ручной ввод разрешён —
     * поведение по умолчанию для фиксированных систем.
     */
    public static FieldConstraints empty() {
        return new FieldConstraints(null, null, null, null, 1, List.of(), true);
    }

    /**
     * Шаг стрелок: единица, если шаг не задан или задан неверно.
     */
    public int stepOrDefault() {
        return step == null || step < 1 ? 1 : step;
    }

    public boolean hasOptions() {
        return options != null && !options.isEmpty();
    }

    /**
     * Числовое значение вне заданных границ.
     */
    public boolean outOfRange(Integer value) {
        if (value == null) {
            return false;
        }
        return (min != null && value < min) || (max != null && value > max);
    }

    /**
     * Значение вне списка предустановленных при запрещённом ручном вводе.
     */
    public boolean notInOptions(String value) {
        if (manualInputAllowed || !hasOptions() || value == null || value.isBlank()) {
            return false;
        }
        return !options.contains(value);
    }
}
