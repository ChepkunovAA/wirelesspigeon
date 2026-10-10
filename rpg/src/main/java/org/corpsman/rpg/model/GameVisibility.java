package org.corpsman.rpg.model;

/**
 * Видимость RPG-игры в зависимости от роли пользователя клуба
 * (аналог {@code TopicVisibility} форума).
 */
public enum GameVisibility {

    /**
     * Публичная: видна всем пользователям (включая гостей).
     */
    PUBLIC,

    /**
     * Открытая: видна резидентам и мастерам, гостям скрыта.
     */
    OPEN,

    /**
     * Гостевая: как открытая, но гости могут участвовать.
     */
    GUEST,

    /**
     * Закрытая: видна ведущему и белому списку.
     */
    CLOSED;

    public String label() {
        return switch (this) {
            case PUBLIC -> "Публичная";
            case OPEN -> "Открытая";
            case GUEST -> "Гостевая";
            case CLOSED -> "Закрытая";
        };
    }
}