package org.corpsman.rpg.dto;

import lombok.Data;
import org.corpsman.rpg.model.AttributeType;
import org.corpsman.rpg.model.GameVisibility;
import org.corpsman.rpg.model.ListItem;
import org.corpsman.rpg.model.ListKind;

import java.util.List;

/**
 * Представление параметра листа для отображения (включая расчётные модификаторы D&D).
 */
@Data
public class AttributeViewDto {

    private String key;

    private Long id;

    private String name;

    private AttributeType type;

    private Integer numericValue;

    private String textValue;

    /**
     * Элементы списка с заголовком, описанием и уровнем (для type=LIST).
     * Поддерживается обратная совместимость со старым форматом (простые строки).
     */
    private List<ListItem> listItems;

    /**
     * Тип списка: {@code SIMPLE} («Простой список») или {@code COMPLEX}
     * («Сложный список»). Для не-списков null.
     */
    private ListKind listKind;

    /**
     * Признак того, что параметр является ячейкой заклинаний (spell_slot_max:N /
     * spell_slot_current:N). Такие параметры рендерятся отдельным блоком «Ячейки».
     */
    private boolean spellSlot;

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
     * Ограничения значения параметра, заданные кастомной системой.
     * null или 1 — шаг по умолчанию, границы не заданы.
     */
    private Integer step;

    private Integer minValue;

    private Integer maxValue;

    /**
     * Разрешён ли ручной ввод. Числовое поле без ручного ввода
     * редактируется только стрелками, текстовое — только выбором.
     */
    private boolean manualInputAllowed = true;

    /**
     * Заголовок группы, внутри которой начинается параметр (null — продолжение группы).
     */
    private String groupHeader;

    /**
     * Подзаголовок внутри группы (например, способность для блока навыков; null — нет).
     */
    private String subHeader;
}