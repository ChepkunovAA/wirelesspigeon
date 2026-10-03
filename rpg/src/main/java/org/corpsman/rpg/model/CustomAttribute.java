package org.corpsman.rpg.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.corpsman.common.model.BaseEntity;

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
}