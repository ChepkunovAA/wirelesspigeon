package org.corpsman.rpg.dto;

import lombok.Data;
import org.corpsman.rpg.model.AttributeType;

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

    private java.util.List<String> listValues;

    /**
     * Построчное представление элементов списка для редактирования в текстовой области.
     */
    private String listText;

    private Integer flags;
}