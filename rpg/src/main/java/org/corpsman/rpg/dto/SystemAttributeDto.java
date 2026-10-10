package org.corpsman.rpg.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.corpsman.rpg.model.AttributeType;
import org.corpsman.rpg.model.ListKind;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Параметр типовой анкеты кастомной ролевой системы.
 * <p>
 * Хранится в {@code GameSystemDefinition.templateAttributes} как JSON.
 * Для {@link AttributeType#LIST} задаётся {@link #listKind}: «Простой список»
 * (строки) или «Сложный список» (заголовок + описание + уровень).
 * <p>
 * Ограничения значения повторяют правила игры для одного листа: числовой
 * параметр — значение по умолчанию, границы, шаг и флаг ручного ввода;
 * текстовый — значение по умолчанию, список вариантов и флаг ручного ввода.
 * При создании игры ограничения переносятся в параметры листа.
 */
@Data
public class SystemAttributeDto {

    private String name;

    private AttributeType type;

    private int position;

    /**
     * Тип списка. Для числовых и текстовых параметров игнорируется.
     * null — «Простой список».
     */
    private ListKind listKind = ListKind.SIMPLE;

    /** Значение по умолчанию числового параметра. */
    private Integer defaultValue;

    /** Нижняя граница числового параметра (null — не ограничена). */
    private Integer minValue;

    /** Верхняя граница числового параметра (null — не ограничена). */
    private Integer maxValue;

    /** Шаг стрелок числового параметра; по умолчанию единица. */
    private Integer stepValue = 1;

    /** Значение по умолчанию текстового параметра. */
    private String defaultText;

    /** Предустановленные значения текстового параметра, по строке на вариант. */
    private List<String> options = new ArrayList<>();

    /**
     * Разрешён ли ручной ввод. По умолчанию снят: числовой параметр
     * редактируется только стрелками, текстовый — только выбором из списка.
     */
    private boolean manualInputAllowed;

    /**
     * Вид текстового параметра: {@link TextMode#FREE} — свободный ввод без
     * списка, {@link TextMode#SELECT} — выбор из списка вариантов.
     * <p>
     * Хранится, а не выводится из {@link #options}: значение, выбранное
     * автором, должно переживать правку системы. У анкет, сохранённых до
     * появления вида, вид выводится из списка вариантов.
     */
    private TextMode textMode;

    /** Подтип текстового параметра. */
    public enum TextMode {
        /** Свободный ввод: список вариантов не задаётся, поле заполняется руками. */
        FREE,
        /** Выбираемый: значение берётся из списка, ручной ввод — по флагу. */
        SELECT
    }

    /**
     * Вид текстового параметра. У старых анкет, где вида не было, он
     * выводится из списка вариантов.
     */
    public TextMode getTextMode() {
        if (textMode != null) {
            return textMode;
        }
        return optionsOrEmpty().isEmpty() ? TextMode.FREE : TextMode.SELECT;
    }

    public void setTextMode(TextMode textMode) {
        this.textMode = textMode;
    }

    /** Подтип текстового поля, выбираемый ли оно. */
    @JsonIgnore
    public boolean isSelectable() {
        return getTextMode() == TextMode.SELECT;
    }

    public ListKind listKindOrDefault() {
        return listKind == null ? ListKind.SIMPLE : listKind;
    }

    /**
     * Шаг стрелок числового параметра; единица, если шаг не задан или неверен.
     */
    public int stepOrDefault() {
        return stepValue == null || stepValue < 1 ? 1 : stepValue;
    }

    /**
     * Варианты текстового параметра без пустых строк.
     */
    public List<String> optionsOrEmpty() {
        if (options == null) {
            return List.of();
        }
        return options.stream().filter(o -> o != null && !o.isBlank()).map(String::trim).toList();
    }

    /**
     * Варианты одной строкой — форма редактирования и предпросмотр системы.
     * Хранение остаётся за {@link #options}, поэтому в JSON это поле не попадает.
     */
    @JsonIgnore
    public String getOptionsText() {
        return String.join("\n", optionsOrEmpty());
    }

    @JsonIgnore
    public void setOptionsText(String text) {
        if (text == null || text.isBlank()) {
            this.options = new ArrayList<>();
            return;
        }
        this.options = java.util.Arrays.stream(text.split("\\R"))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toCollection(ArrayList::new));
    }
}