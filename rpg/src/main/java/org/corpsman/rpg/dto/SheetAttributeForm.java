package org.corpsman.rpg.dto;

import lombok.Data;
import org.corpsman.rpg.model.AttributeType;
import org.corpsman.rpg.model.ListKind;

/**
 * Редактируемое значение одного параметра листа.
 */
@Data
public class SheetAttributeForm {

    private String key;

    private String name;

    private AttributeType type;

    private Integer numericValue;

    private String textValue;

    /**
     * Элементы списка в виде JSON: [{"header":"...","description":"...","level":3}, ...].
     * Формируется на клиенте из карточек и модального окна добавления.
     */
    private String listJson;

    /**
     * Построчное представление элементов «простого» списка (по одному элементу
     * в строке). Заполняется при сохранении простого списка.
     */
    private String listText;

    /**
     * Тип списка: {@code SIMPLE} («Простой список») или {@code COMPLEX}
     * («Сложный список»). null — по умолчанию простой.
     */
    private ListKind listKind;

    private Integer flags;
}
