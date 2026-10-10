package org.corpsman.rpg.dto;

/**
 * Один вид монет в блоке «Деньги»: подпись, сокращение, значение и ключи
 * соседних разрядов для кнопок обмена.
 *
 * @param key        ключ параметра листа
 * @param label      название вида монет
 * @param abbr       сокращение (зм, см, мм)
 * @param param      имя поля ручного ввода
 * @param value      текущее количество монет
 * @param lower      ключ младшего разряда, если он есть
 * @param lowerAbbr  сокращение младшего разряда
 * @param upper      ключ старшего разряда, если он есть
 */
public record MoneyKindDto(String key, String label, String abbr, String param, int value,
                           String lower, String lowerAbbr, String upper) {
}
