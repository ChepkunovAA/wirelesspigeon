package org.corpsman.rpg.model;

/**
 * Ролевая система игры.
 */
public enum GameSystem {

    /**
     * Словесная: никаких дополнительных атрибутов листа.
     */
    VERBAL,

    /**
     * D&amp;D: заранее заданные параметры с частичной автоматизацией.
     */
    DND,

    /**
     * Кастомная: набор параметров задаёт ведущий (3 типа: числовой, текстовый, список).
     */
    CUSTOM;

    public String label() {
        return switch (this) {
            case VERBAL -> "Словесная";
            case DND -> "D&D";
            case CUSTOM -> "Кастомная";
        };
    }
}