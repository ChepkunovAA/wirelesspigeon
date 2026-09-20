package org.corpsman.rpg.dto;

import lombok.Data;
import org.corpsman.rpg.model.AttributeType;
import org.corpsman.rpg.model.GameVisibility;

import java.util.List;

/**
 * Представление параметра листа для отображения (включая расчётные модификаторы D&amp;D).
 */
@Data
public class AttributeViewDto {

    private String key;

    private Long id;

    private String name;

    private AttributeType type;

    private Integer numericValue;

    private String textValue;

    private List<String> listValues;

    private int flags;

    private int position;

    private GameVisibility visibility;

    private Integer modifier;

    private String abilityKey;

    private int flagMax;

    /**
     * Варианты выпадающего списка (класс, раса).
     */
    private List<String> options;

    /**
     * Метка пункта «Другое» для выпадающего списка (null — пункта нет).
     */
    private String otherOptionLabel;

    /**
     * Заголовок группы, внутри которой начинается параметр (null — продолжение группы).
     */
    private String groupHeader;

    /**
     * Подзаголовок внутри группы (например, способность для блока навыков; null — нет).
     */
    private String subHeader;
}