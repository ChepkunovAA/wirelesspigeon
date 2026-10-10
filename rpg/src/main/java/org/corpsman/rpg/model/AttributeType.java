package org.corpsman.rpg.model;

/**
 * Тип параметра листа персонажа.
 */
public enum AttributeType {

    /**
     * Числовой параметр.
     */
    NUMERIC,

    /**
     * Текстовый параметр.
     */
    TEXT,

    /**
     * Список текстовых строк (например, инвентарь или заклинания).
     */
    LIST
}