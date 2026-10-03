package org.corpsman.rpg.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.corpsman.common.model.BaseEntity;

/**
 * Параметр листа персонажа.
 * <p>
 * Типизированная модель: числовой / текстовый / список. Списки хранятся как JSON-массив
 * строк в {@code listValues}. Для элементов списка (type=LIST) можно хранить
 * заголовок и описание каждого элемента в JSON: [{"header":"...", "description":"..."}, ...].
 * Флаги ({@code flags}, битовая маска) используются ролевой
 * системой: для D&D — владение спасброском (бит 0) и владение навыком (тернарный флаг).
 * Каждый параметр имеет собственную видимость.
 */
@Entity
@Table(name = "rpg_character_attributes",
        uniqueConstraints = @UniqueConstraint(name = "uk_rpg_attr_sheet_key", columnNames = {"sheet_id", "attr_key"}))
@Getter
@Setter
public class SheetAttribute extends BaseEntity {

    @Column(name = "sheet_id", nullable = false)
    private Long sheetId;

    /**
     * Стабильный ключ параметра (например, {@code level}, {@code strength},
     * {@code save:wisdom}, {@code skill:stealth}, {@code custom:<id>}).
     */
    @Column(name = "attr_key", nullable = false, length = 64)
    private String key;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "attr_type", nullable = false, length = 20)
    private AttributeType type;

    @Column(name = "numeric_value")
    private Integer numericValue;

    @Column(name = "text_value", length = 1000000)
    private String textValue;

    /**
     * JSON-массив для списков (type=LIST).
     * Формат определяется {@link #listKind}: для {@code SIMPLE} — массив строк
     * {@code ["Факел", "Хлеб"]}, для {@code COMPLEX} — массив объектов
     * {@code [{"header":"...","description":"...","level":1}, ...]}.
     * Старые данные читаются в обоих форматах независимо от listKind.
     */
    @Column(name = "list_values", length = 1000000)
    private String listValues;

    /**
     * Тип списка: {@code SIMPLE} или {@code COMPLEX}. null — старый лист,
     * формат определяется по фактическому JSON.
     */
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "list_kind", length = 16)
    private ListKind listKind;

    /**
     * Битовая маска флагов ролевой системы.
     */
    @Column(nullable = false)
    private int flags;

    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GameVisibility visibility = GameVisibility.OPEN;
}