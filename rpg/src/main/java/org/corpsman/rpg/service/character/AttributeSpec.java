package org.corpsman.rpg.service.character;

import org.corpsman.rpg.model.AttributeType;
import org.corpsman.rpg.model.ListKind;

/**
 * Описание параметра листа, объявляемого ролевой системой.
 *
 * @param key          стабильный ключ (сохраняется в {@code attr_key})
 * @param name         отображаемое имя
 * @param type         тип параметра
 * @param order        порядок отображения
 * @param defaultValue значение числового параметра по умолчанию (для новых листов), либо {@code null}
 * @param listKind     тип списка для {@link AttributeType#LIST}: {@code SIMPLE} или
 *                     {@code COMPLEX}. Для остальных типов игнорируется.
 */
public record AttributeSpec(String key, String name, AttributeType type, int order, Integer defaultValue,
                            ListKind listKind) {

    public AttributeSpec(String key, String name, AttributeType type, int order) {
        this(key, name, type, order, null, null);
    }

    public AttributeSpec(String key, String name, AttributeType type, int order, Integer defaultValue) {
        this(key, name, type, order, defaultValue, null);
    }

    /**
     * Тип списка с запасным вариантом: для списков без явного указания —
     * {@link ListKind#SIMPLE} (старый формат, строки).
     */
    public ListKind listKindOrDefault() {
        return listKind == null ? ListKind.SIMPLE : listKind;
    }
}
