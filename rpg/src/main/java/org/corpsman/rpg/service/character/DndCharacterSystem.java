package org.corpsman.rpg.service.character;

import org.corpsman.rpg.model.AttributeType;
import org.corpsman.rpg.model.ListKind;
import org.corpsman.rpg.model.CharacterSheet;
import org.corpsman.rpg.model.Game;
import org.corpsman.rpg.model.GameSystem;
import org.corpsman.rpg.model.SheetAttribute;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Система D&amp;D: фиксированные параметры с частичной автоматизацией.
 * <p>
 * Хранение: на листе лежат исходные значения (уровень, хиты, характеристики,
 * класс, раса, заклинания/черты/умения) и флаги владения спасбросками (бинарный)
 * и навыками (тернарный). Строки спасбросков и навыков существуют как параметры
 * (хранят флаги), но их числовое значение вычисляется на чтении.
 */
@Component
public class DndCharacterSystem implements CharacterSystem {

    public static final String KEY_LEVEL = "level";
    public static final String KEY_HIT_POINTS = "hit_points";
    public static final String KEY_CLASS = "class";
    public static final String KEY_RACE = "race";
    public static final String KEY_BACKGROUND = "background";
    public static final String KEY_ARMOR_CLASS = "armor_class";
    public static final String KEY_SPELLS = "spells";
    public static final String KEY_FEATS = "feats";
    public static final String KEY_SKILLS_LIST = "skills";
    public static final String KEY_PROFICIENCY_BONUS = "proficiency_bonus";
    /** Монеты хранятся тремя числовыми параметрами листа. */
    public static final String PREFIX_MONEY = "money:";

    public static final String MONEY_GOLD = "money:gold";

    public static final String MONEY_SILVER = "money:silver";

    public static final String MONEY_COPPER = "money:copper";

    /** Сколько монет младшего разряда даёт одна монета старшего. */
    public static final int MONEY_RATE = 10;

    /** Разряды от старшего к младшему. */
    public static final List<String> MONEY_KINDS = List.of(MONEY_GOLD, MONEY_SILVER, MONEY_COPPER);

    public static final String PREFIX_SPELL_SLOT_MAX = "spell_slot_max:";
    public static final String PREFIX_SPELL_SLOT_CURRENT = "spell_slot_current:";

    public static final String PREFIX_ABILITY = "ability:";
    public static final String PREFIX_SAVE = "save:";
    public static final String PREFIX_SKILL = "skill:";

    /**
     * Кость-коэффициент: флаг навыка 1 = владение, 2 = компетентность (экспертиза).
     */
    public static final int SKILL_FLAG_NONE = 0;
    public static final int SKILL_FLAG_PROFICIENT = 1;
    public static final int SKILL_FLAG_EXPERTISE = 2;

    private static final Map<String, String> ABILITY_NAMES = new LinkedHashMap<>();
    private static final Map<String, List<String[]>> SKILLS = new LinkedHashMap<>();
    private static final List<String> CLASS_OPTIONS = List.of(
            "Воин", "Паладин", "Плут", "Следопыт", "Колдун", "Чародей",
            "Волшебник", "Жрец", "Друид", "Бард", "Монах", "Варвар");
    private static final List<String> RACE_OPTIONS = List.of(
            "Человек", "Эльф", "Полуэльф", "Гном", "Полурослик", "Дварф",
            "Полуорк", "Драконорождённый", "Тифлинг");
    private static final List<String> BACKGROUND_OPTIONS = List.of(
            "Артист", "Беспризорник", "Благородный", "Гильдейский ремесленник", "Моряк",
            "Мудрец", "Народный герой", "Отшельник", "Пират", "Преступник",
            "Прислужник", "Солдат", "Чужеземец", "Шарлатан");

    static {
        ABILITY_NAMES.put("strength", "Сила");
        ABILITY_NAMES.put("dexterity", "Ловкость");
        ABILITY_NAMES.put("constitution", "Телосложение");
        ABILITY_NAMES.put("intelligence", "Интеллект");
        ABILITY_NAMES.put("wisdom", "Мудрость");
        ABILITY_NAMES.put("charisma", "Харизма");

        SKILLS.put("strength", java.util.Collections.singletonList(new String[]{"athletics", "Атлетика"}));
        SKILLS.put("dexterity", List.of(
                new String[]{"acrobatics", "Акробатика"},
                new String[]{"sleight_of_hand", "Ловкость рук"},
                new String[]{"stealth", "Скрытность"}));
        SKILLS.put("intelligence", List.of(
                new String[]{"history", "История"},
                new String[]{"arcana", "Магия"},
                new String[]{"nature", "Природа"},
                new String[]{"investigation", "Расследование"},
                new String[]{"religion", "Религия"}));
        SKILLS.put("wisdom", List.of(
                new String[]{"perception", "Восприятие"},
                new String[]{"survival", "Выживание"},
                new String[]{"medicine", "Медицина"},
                new String[]{"insight", "Проницательность"},
                new String[]{"animal_handling", "Уход за животными"}));
        SKILLS.put("charisma", List.of(
                new String[]{"performance", "Выступление"},
                new String[]{"intimidation", "Запугивание"},
                new String[]{"deception", "Обман"},
                new String[]{"persuasion", "Убеждение"}));
    }

    public static List<String> abilityKeys() {
        return List.copyOf(ABILITY_NAMES.keySet());
    }

    public static String abilityName(String key) {
        return ABILITY_NAMES.get(key);
    }

    @Override
    public GameSystem system() {
        return GameSystem.DND;
    }

    @Override
    public List<AttributeSpec> template(Game game) {
        List<AttributeSpec> specs = new ArrayList<>();
        specs.add(new AttributeSpec(KEY_LEVEL, "Уровень", AttributeType.NUMERIC, 10, 1));
        specs.add(new AttributeSpec(KEY_CLASS, "Класс", AttributeType.TEXT, 20));
        specs.add(new AttributeSpec(KEY_RACE, "Раса", AttributeType.TEXT, 30));
        specs.add(new AttributeSpec(KEY_BACKGROUND, "Предыстория", AttributeType.TEXT, 35));
        specs.add(new AttributeSpec(KEY_HIT_POINTS, "Хиты", AttributeType.NUMERIC, 40, 20));
        specs.add(new AttributeSpec(KEY_ARMOR_CLASS, "Класс доспеха", AttributeType.NUMERIC, 45, 10));
        int pos = 100;
        for (String ability : abilityKeys()) {
            specs.add(new AttributeSpec(PREFIX_ABILITY + ability, abilityName(ability), AttributeType.NUMERIC, pos, 10));
            pos += 10;
        }
        pos = 200;
        for (String ability : abilityKeys()) {
            specs.add(new AttributeSpec(PREFIX_SAVE + ability, "Спасбросок: " + abilityName(ability), AttributeType.NUMERIC, pos));
            pos += 10;
        }
        pos = 300;
        for (var entry : SKILLS.entrySet()) {
            for (String[] skill : entry.getValue()) {
                specs.add(new AttributeSpec(PREFIX_SKILL + skill[0], skill[1], AttributeType.NUMERIC, pos));
                pos += 1;
            }
        }
        // Spell slots (levels 1-9): max and current
        for (int level = 1; level <= 9; level++) {
            specs.add(new AttributeSpec(PREFIX_SPELL_SLOT_MAX + level, "Ячейки " + level + " ур. (макс.)", AttributeType.NUMERIC, 550 + level * 10, 0));
            specs.add(new AttributeSpec(PREFIX_SPELL_SLOT_CURRENT + level, "Ячейки " + level + " ур. (текущ.)", AttributeType.NUMERIC, 550 + level * 10 + 5, 0));
        }
        // Деньги выводятся отдельным блоком, в общем списке параметров их нет.
        specs.add(new AttributeSpec(MONEY_GOLD, "Золотые", AttributeType.NUMERIC, 545, 0));
        specs.add(new AttributeSpec(MONEY_SILVER, "Серебряные", AttributeType.NUMERIC, 546, 0));
        specs.add(new AttributeSpec(MONEY_COPPER, "Медные", AttributeType.NUMERIC, 547, 0));
        // Списки D&D — «сложные»: элемент имеет заголовок, описание и уровень.
        specs.add(new AttributeSpec(KEY_FEATS, "Черты", AttributeType.LIST, 510, null, ListKind.COMPLEX));
        specs.add(new AttributeSpec(KEY_SKILLS_LIST, "Умения", AttributeType.LIST, 520, null, ListKind.COMPLEX));
        specs.add(new AttributeSpec(KEY_SPELLS, "Заклинания", AttributeType.LIST, 530, null, ListKind.COMPLEX));
        return specs;
    }

    /**
     * Является ли параметр одним из видов монет.
     */
    public static boolean isMoneyKey(String key) {
        return key != null && key.startsWith(PREFIX_MONEY);
    }

    /**
     * Человеческое название вида монет.
     */
    public static String moneyLabel(String key) {
        return switch (key) {
            case MONEY_GOLD -> "Золотые";
            case MONEY_SILVER -> "Серебряные";
            case MONEY_COPPER -> "Медные";
            default -> key;
        };
    }

    @Override
    public List<String> optionsFor(String key) {
        if (KEY_CLASS.equals(key)) {
            return CLASS_OPTIONS;
        }
        if (KEY_RACE.equals(key)) {
            return RACE_OPTIONS;
        }
        if (KEY_BACKGROUND.equals(key)) {
            return BACKGROUND_OPTIONS;
        }
        return List.of();
    }

    @Override
    public String otherOptionLabel(String key) {
        if (KEY_CLASS.equals(key)) {
            return "Другой";
        }
        if (KEY_RACE.equals(key)) {
            return "Другая";
        }
        if (KEY_BACKGROUND.equals(key)) {
            return "Другая";
        }
        return "";
    }

    /**
     * Ограничения параметров D&amp;D.
     * <p>
     * Виды монет: количество не ниже нуля, ввод вручную разрешён — сумма
     * задаётся сразу по всем разрядам. Остальные числа (уровень, хиты, класс
     * доспеха, характеристики, спасброски, навыки, ячейки) правятся только
     * стрелками: ручной ввод в D&amp;D есть лишь у денег.
     */
    @Override
    public FieldConstraints constraints(String key) {
        if (key == null) {
            return FieldConstraints.empty();
        }
        if (isMoneyKey(key)) {
            return new FieldConstraints(null, null, 0, null, 1, List.of(), true);
        }
        if (isArrowNumericKey(key)) {
            return new FieldConstraints(null, null, null, null, 1, List.of(), false);
        }
        return FieldConstraints.empty();
    }

    /**
     * Числовой параметр D&amp;D, который меняют только стрелками.
     */
    private static boolean isArrowNumericKey(String key) {
        return KEY_LEVEL.equals(key)
                || KEY_HIT_POINTS.equals(key)
                || KEY_ARMOR_CLASS.equals(key)
                || key.startsWith(PREFIX_ABILITY)
                || key.startsWith(PREFIX_SAVE)
                || key.startsWith(PREFIX_SKILL)
                || key.startsWith(PREFIX_SPELL_SLOT_MAX)
                || key.startsWith(PREFIX_SPELL_SLOT_CURRENT);
    }

    @Override
    public Map<String, Integer> computedModifiers(CharacterSheet sheet, List<SheetAttribute> attrs) {
        Map<String, Integer> result = new HashMap<>();
        Map<String, SheetAttribute> byKey = new HashMap<>();
        for (SheetAttribute a : attrs) {
            byKey.put(a.getKey(), a);
        }
        int level = numeric(byKey.get(KEY_LEVEL));
        int proficiencyBonus = proficiencyBonus(level);
        result.put(KEY_PROFICIENCY_BONUS, proficiencyBonus);

        for (String ability : abilityKeys()) {
            int score = numeric(byKey.get(PREFIX_ABILITY + ability));
            int mod = abilityModifier(score);
            result.put(PREFIX_ABILITY + ability, mod);
            result.put(PREFIX_SAVE + ability, mod + (proficientSave(byKey.get(PREFIX_SAVE + ability)) ? proficiencyBonus : 0));
            List<String[]> skills = SKILLS.get(ability);
            if (skills == null) {
                continue;
            }
            for (String[] skill : skills) {
                int flag = skillFlags(byKey.get(PREFIX_SKILL + skill[0]));
                int bonus = switch (flag) {
                    case SKILL_FLAG_EXPERTISE -> proficiencyBonus * 2;
                    case SKILL_FLAG_PROFICIENT -> proficiencyBonus;
                    default -> 0;
                };
                result.put(PREFIX_SKILL + skill[0], mod + bonus);
            }
        }
        return result;
    }

    @Override
    public int flagMax(String key) {
        if (key.startsWith(PREFIX_SAVE)) {
            return 1;
        }
        if (key.startsWith(PREFIX_SKILL)) {
            return 2;
        }
        return 0;
    }

    /**
     * Бонус мастерства: 2 + (уровень - 1) // 4.
     *
     * @param level уровень
     * @return бонус мастерства
     */
    public static int proficiencyBonus(int level) {
        return 2 + Math.floorDiv(Math.max(level, 1) - 1, 4);
    }

    /**
     * Модификатор характеристики: (значение - 10) // 2 (нацело, вниз для отрицательных).
     *
     * @param value значение характеристики
     * @return модификатор
     */
    public static int abilityModifier(int value) {
        return Math.floorDiv(value - 10, 2);
    }

    private static int numeric(SheetAttribute a) {
        return a == null || a.getNumericValue() == null ? 0 : a.getNumericValue();
    }

    private static boolean proficientSave(SheetAttribute a) {
        return a != null && ((a.getFlags() & 1) != 0);
    }

    private static int skillFlags(SheetAttribute a) {
        if (a == null) {
            return SKILL_FLAG_NONE;
        }
        int f = a.getFlags() & 3;
        return f;
    }

    /**
     * Кость-навык его характеристика.
     *
     * @param skillKey ключ навыка (без префикса)
     * @return ключ характеристики или {@code null}
     */
    public static String abilityForSkill(String skillKey) {
        for (var entry : SKILLS.entrySet()) {
            for (String[] skill : entry.getValue()) {
                if (skill[0].equals(skillKey)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    /**
     * Карта «ключ навыка → ключ характеристики» для группировки проверок.
     */
    public static Map<String, String> skillAbilityMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (var entry : SKILLS.entrySet()) {
            for (String[] skill : entry.getValue()) {
                map.put(skill[0], entry.getKey());
            }
        }
        return map;
    }
}