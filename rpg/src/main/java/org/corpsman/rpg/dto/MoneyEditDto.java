package org.corpsman.rpg.dto;

/**
 * Разряд монет для формы редактирования листа.
 * <p>
 * Значение правится рядом с остальными разрядами: стрелками на единицу и
 * кнопками обмена с младшим разрядом по курсу 1:10 — одна монета этого вида
 * превращается в десять младшего и наоборот. У младшего разряда («медные»)
 * младше ничего нет, поэтому кнопок обмена у него нет.
 *
 * @param key       ключ параметра листа ({@code money:gold})
 * @param label     название вида монет
 * @param abbr      сокращение (зм, см, мм)
 * @param value     текущее количество монет
 * @param attrIndex индекс параметра в списке атрибутов формы
 * @param min       нижняя граница (у монет — ноль)
 * @param step      шаг стрелок
 * @param lowerKey  ключ младшего разряда, если он есть
 * @param lowerAbbr сокращение младшего разряда
 */
public record MoneyEditDto(String key, String label, String abbr, int value, int attrIndex,
                           Integer min, int step, String lowerKey, String lowerAbbr) {
}