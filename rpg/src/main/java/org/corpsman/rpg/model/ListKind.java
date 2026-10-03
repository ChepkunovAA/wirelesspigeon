package org.corpsman.rpg.model;

/**
 * Тип списка листа ({@code type=LIST}).
 * <p>
 * {@code SIMPLE} — «Простой список»: элемент это одна строка (например,
 * инвентарь). Хранится JSON-массивом строк {@code ["Факел", "Хлеб"]}.
 * <p>
 * {@code COMPLEX} — «Сложный список»: элемент имеет заголовок, описание
 * и уровень (например, умения, черты, заклинания в D&amp;D). Хранится
 * JSON-массивом объектов
 * {@code [{"header":"...","description":"...","level":1}, ...]}.
 */
public enum ListKind {

    /** Простой список: только строки. */
    SIMPLE,

    /** Сложный список: заголовок + описание + уровень. */
    COMPLEX;

    /**
     * Разбор значения с запасным вариантом: неизвестное или отсутствующее
     * значение трактуется как {@link #SIMPLE} (старый формат).
     */
    public static ListKind parse(String value) {
        if (value == null) {
            return SIMPLE;
        }
        for (ListKind kind : values()) {
            if (kind.name().equalsIgnoreCase(value.trim())) {
                return kind;
            }
        }
        return SIMPLE;
    }
}
