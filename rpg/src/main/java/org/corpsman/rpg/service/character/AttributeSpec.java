package org.corpsman.rpg.service.character;

import org.corpsman.rpg.model.AttributeType;

/**
 * Описание параметра листа, объявляемого ролевой системой.
 *
 * @param key          стабильный ключ (сохраняется в {@code attr_key})
 * @param name         отображаемое имя
 * @param type         тип параметра
 * @param order        порядок отображения
 * @param defaultValue значение числового параметра по умолчанию (для новых листов), либо {@code null}
 */
public record AttributeSpec(String key, String name, AttributeType type, int order, Integer defaultValue) {

    public AttributeSpec(String key, String name, AttributeType type, int order) {
        this(key, name, type, order, null);
    }
}