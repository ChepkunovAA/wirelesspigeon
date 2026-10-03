package org.corpsman.rpg.dto;

import lombok.Data;
import org.corpsman.rpg.model.AttributeType;
import org.corpsman.rpg.model.ListKind;

/**
 * Параметр типовой анкеты кастомной ролевой системы.
 * <p>
 * Хранится в {@code GameSystemDefinition.templateAttributes} как JSON.
 * Для {@link AttributeType#LIST} задаётся {@link #listKind}: «Простой список»
 * (строки) или «Сложный список» (заголовок + описание + уровень).
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

    public ListKind listKindOrDefault() {
        return listKind == null ? ListKind.SIMPLE : listKind;
    }
}
