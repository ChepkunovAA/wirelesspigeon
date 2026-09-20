package org.corpsman.rpg.service.character;

import lombok.Getter;
import lombok.Setter;
import org.corpsman.rpg.model.AttributeType;
import org.corpsman.rpg.model.GameVisibility;

import java.util.List;

/**
 * Представление параметра листа для отображения.
 * <p>
 * Содержит исходные значения, флаги ролевой системы и расчётный модификатор
 * (для D&amp;D заполняется автоматизацией при рендере).
 */
@Getter
@Setter
public class AttributeView {

    private String key;

    private String name;

    private AttributeType type;

    private Integer numericValue;

    private String textValue;

    private List<String> listValues;

    private int flags;

    private int position;

    private GameVisibility visibility;

    /**
     * Расчётный модификатор (для D&amp;D): бонус мастерства, моды характеристик,
     * моды спасбросков и навыков; {@code null}, если не применим.
     */
    private Integer modifier;

    /**
     * Характеристика, к которой относится навык/спасбросок (для подписи), либо {@code null}.
     */
    private String abilityKey;

    /**
     * Максимальное значение флага для UI: 0 — без флагов, 1 — бинарный,
     * 2 — тернарный (владение/компетентность).
     */
    private int flagMax;

    public AttributeView() {
    }

    public static AttributeView of(String key, String name, AttributeType type, Integer numericValue,
                                   String textValue, List<String> listValues, int flags, int position,
                                   GameVisibility visibility) {
        AttributeView v = new AttributeView();
        v.key = key;
        v.name = name;
        v.type = type;
        v.numericValue = numericValue;
        v.textValue = textValue;
        v.listValues = listValues;
        v.flags = flags;
        v.position = position;
        v.visibility = visibility;
        return v;
    }
}