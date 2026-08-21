package org.corpsman.cinema.model;

import lombok.Getter;

/**
 * Цензурный рейтинг просмотренного фильма.
 */
@Getter
public enum CensorshipRating {

    /**
     * Целомудренно.
     */
    CHASTE("Целомудренно", 1),

    /**
     * Намёки.
     */
    HINTS("Намёки", 2),

    /**
     * Эротика.
     */
    EROTICA("Эротика", 3),

    /**
     * Порно.
     */
    PORNOGRAPHY("Порно", 4),

    /**
     * Кошмар.
     */
    NIGHTMARE("Кошмар", 5);

    private final String displayName;
    private final int value;

    CensorshipRating(String displayName, int value) {
        this.displayName = displayName;
        this.value = value;
    }
}