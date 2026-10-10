package org.corpsman.rpg.model;

/**
 * Статус RPG-игры.
 */
public enum GameStatus {

    /**
     * Идёт набор игроков.
     */
    RECRUITING,

    /**
     * В процессе игры.
     */
    ACTIVE,

    /**
     * Завершена.
     */
    CLOSED;

    public String label() {
        return switch (this) {
            case RECRUITING -> "Набор игроков";
            case ACTIVE -> "В процессе";
            case CLOSED -> "Завершена";
        };
    }
}