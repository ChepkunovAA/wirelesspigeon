package org.corpsman.rpg.service.character;

import org.corpsman.rpg.model.AttributeType;
import org.corpsman.rpg.model.CharacterSheet;
import org.corpsman.rpg.model.Game;
import org.corpsman.rpg.model.SheetAttribute;

import java.util.List;
import java.util.Map;

/**
 * Стратегия ролевой системы листа персонажа.
 * <p>
 * Расширяемость: новая ролевая система = новый enum в {@link org.corpsman.rpg.model.GameSystem}
 * и новая реализация этого интерфейса, зарегистрированная в
 * {@link CharacterSystemRegistry}. Ядро (листы, заявки, комнаты) не меняется.
 */
public interface CharacterSystem {

    /**
     * Ключ системы, которую обслуживает стратегия.
     *
     * @return ролевая система
     */
    org.corpsman.rpg.model.GameSystem system();

    /**
     * Параметры, создаваемые на каждом листе персонажа при его создании.
     *
     * @param game игра (для кастомных параметров)
     * @return список описаний параметров
     */
    List<AttributeSpec> template(Game game);

    /**
     * Расчётные модификаторы по ключам параметров листа.
     * <p>
     * Для D&amp;D: бонус мастерства, модификаторы характеристик, спасбросков и навыков.
     * Для прочих систем — пустая карта (модификаторы не вычисляются).
     *
     * @param sheet лист
     * @param attrs параметры листа
     * @return карта «ключ параметра → рассчитанный модификатор»
     */
    Map<String, Integer> computedModifiers(CharacterSheet sheet, List<SheetAttribute> attrs);

    /**
     * Тип значения флага для UI. 0 — флагов нет, 1 — бинарный (владение),
     * 2 — тернарный (нет/владение/компетентность).
     *
     * @param key ключ параметра
     * @return максимальное значение флага
     */
    default int flagMax(String key) {
        return 0;
    }

    /**
     * Плейсхолдер значения по умолчанию для типа.
     *
     * @param type тип
     * @return строка-подсказка шаблона
     */
    default String hint(AttributeType type) {
        return "";
    }

    /**
     * Варианты выпадающего списка для текстового параметра (например, класс и раса).
     *
     * @param key ключ параметра
     * @return список вариантов (по умолчанию пустой)
     */
    default List<String> optionsFor(String key) {
        return List.of();
    }

    /**
     * Метка пункта «Другое» для выпадающего списка текстового параметра.
     * <p>
     * Если вернуть непустую строку, в выпадающем списке появляется пункт
     * «{@code метка…}», при выборе которого можно ввести своё значение вручную.
     *
     * @param key ключ параметра
     * @return метка пункта «Другое» (по умолчанию пустая — пункт не показывается)
     */
    default String otherOptionLabel(String key) {
        return "";
    }

    /**
     * Ограничения значения параметра: значение по умолчанию, границы, шаг,
     * предустановленные варианты и флаг ручного ввода. Для фиксированных
     * систем ограничений нет.
     *
     * @param key ключ параметра
     * @return ограничения параметра (по умолчанию пустые)
     */
    default FieldConstraints constraints(String key) {
        return FieldConstraints.empty();
    }
}