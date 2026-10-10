package org.corpsman.rpg.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.corpsman.common.model.BaseEntity;

import java.util.List;

/**
 * Определение кастомного параметра листа для системы «Кастомная».
 */
@Entity
@Table(name = "rpg_custom_attributes")
@Getter
@Setter
public class CustomAttribute extends BaseEntity {

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(nullable = false, length = 200)
    private String name;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "attr_type", nullable = false, length = 20)
    private AttributeType type;

    @Column(nullable = false)
    private int position;

    /**
     * Тип списка для параметров типа {@link AttributeType#LIST}:
     * {@code SIMPLE} («Простой список») или {@code COMPLEX} («Сложный список»).
     * Для числовых и текстовых параметров не используется.
     */
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "list_kind", length = 16)
    private ListKind listKind = ListKind.SIMPLE;

    public ListKind listKindOrDefault() {
        return listKind == null ? ListKind.SIMPLE : listKind;
    }

    /**
     * Значение по умолчанию числового параметра.
     */
    @Column(name = "default_value")
    private Integer defaultValue;

    /**
     * Значение по умолчанию текстового параметра.
     */
    @Column(name = "default_text", length = 200)
    private String defaultText;

    /**
     * Нижняя граница числового параметра (null — не ограничена).
     */
    @Column(name = "min_value")
    private Integer minValue;

    /**
     * Верхняя граница числового параметра (null — не ограничена).
     */
    @Column(name = "max_value")
    private Integer maxValue;

    /**
     * Шаг стрелок числового параметра.
     */
    @Column(name = "step_value")
    private Integer stepValue = 1;

    /**
     * Предустановленные значения текстового параметра, по строке на вариант.
     */
    @Column(name = "options_text", length = 1000000)
    private String optionsText;

    /**
     * Разрешён ли ручной ввод. По умолчанию снят: числовой параметр
     * редактируется только стрелками, текстовый — только выбором из списка.
     */
    @Column(name = "manual_input_allowed")
    private boolean manualInputAllowed;

    /**
     * Предустановленные значения текстового параметра.
     */
    public List<String> getOptions() {
        if (optionsText == null || optionsText.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(optionsText.split("\\R"))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    public void setOptions(List<String> options) {
        this.optionsText = options == null || options.isEmpty()
                ? null : String.join("\n", options);
    }

    /**
     * Шаг стрелок; по умолчанию единица.
     */
    public int stepOrDefault() {
        return stepValue == null || stepValue < 1 ? 1 : stepValue;
    }
}