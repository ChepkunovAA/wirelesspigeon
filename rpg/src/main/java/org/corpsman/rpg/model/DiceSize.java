package org.corpsman.rpg.model;

import lombok.Getter;

/**
 * Тип игральной кости.
 */
public enum DiceSize {
    D2(2), D4(4), D6(6), D8(8), D10(10), D12(12), D20(20), D100(100);

    @Getter
    private final int faces;

    DiceSize(int faces) {
        this.faces = faces;
    }

    /**
     * Ищет кость по количеству граней.
     *
     * @param faces количество граней
     * @return кость или {@code null}, если такой нет
     */
    public static DiceSize byFaces(int faces) {
        for (DiceSize size : values()) {
            if (size.faces == faces) {
                return size;
            }
        }
        return null;
    }

    /**
     * Имя кости в формате «d6».
     *
     * @return строковое представление
     */
    public String label() {
        return "d" + faces;
    }

    /**
     * Ищет кость по имени в формате «d6».
     *
     * @param label имя кости
     * @return кость или {@code null}, если такой нет
     */
    public static DiceSize byLabel(String label) {
        for (DiceSize size : values()) {
            if (size.label().equalsIgnoreCase(label)) {
                return size;
            }
        }
        return null;
    }
}