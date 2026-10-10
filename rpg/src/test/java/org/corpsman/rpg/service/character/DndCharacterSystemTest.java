package org.corpsman.rpg.service.character;

import org.corpsman.rpg.model.AttributeType;
import org.corpsman.rpg.model.Game;
import org.corpsman.rpg.model.GameSystem;
import org.corpsman.rpg.model.SheetAttribute;
import org.corpsman.rpg.service.character.AttributeSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class DndCharacterSystemTest {

    private final DndCharacterSystem system = new DndCharacterSystem();

    private Game game() {
        Game g = new Game();
        g.setSystem(GameSystem.DND);
        return g;
    }

    private SheetAttribute attr(String key, Integer value) {
        SheetAttribute a = new SheetAttribute();
        a.setKey(key);
        a.setNumericValue(value);
        return a;
    }

    @Test
    void templateContainsAllRequiredFields() {
        List<AttributeSpec> specs = system.template(game());

        assertThat(specs).extracting(AttributeSpec::key)
            .contains(
                "level", "class", "race", "background",
                "hit_points", "armor_class",
                "ability:strength", "ability:dexterity", "ability:constitution",
                "ability:intelligence", "ability:wisdom", "ability:charisma",
                "save:strength", "save:dexterity", "save:constitution",
                "save:intelligence", "save:wisdom", "save:charisma",
                "skill:athletics", "skill:acrobatics", "skill:sleight_of_hand", "skill:stealth",
                "skill:history", "skill:arcana", "skill:nature", "skill:investigation", "skill:religion",
                "skill:perception", "skill:survival", "skill:medicine", "skill:insight", "skill:animal_handling",
                "skill:performance", "skill:intimidation", "skill:deception", "skill:persuasion",
                "feats", "skills", "spells"
            );

        // Spell slots 1-9
        for (int level = 1; level <= 9; level++) {
            assertThat(specs).extracting(AttributeSpec::key)
                .contains("spell_slot_max:" + level, "spell_slot_current:" + level);
        }
    }

    @Test
    void templateBackgroundPositionBetweenRaceAndHitPoints() {
        List<AttributeSpec> specs = system.template(game());

        int racePos = specs.stream().filter(s -> "race".equals(s.key())).findFirst().get().order();
        int backgroundPos = specs.stream().filter(s -> "background".equals(s.key())).findFirst().get().order();
        int hpPos = specs.stream().filter(s -> "hit_points".equals(s.key())).findFirst().get().order();

        assertThat(backgroundPos).isGreaterThan(racePos).isLessThan(hpPos);
    }

    @Test
    void templateArmorClassBelowHitPoints() {
        List<AttributeSpec> specs = system.template(game());

        int hpPos = specs.stream().filter(s -> "hit_points".equals(s.key())).findFirst().get().order();
        int acPos = specs.stream().filter(s -> "armor_class".equals(s.key())).findFirst().get().order();

        assertThat(acPos).isGreaterThan(hpPos);
    }

    @Test
    void templateSpellSlotsHaveMaxAndCurrent() {
        List<AttributeSpec> specs = system.template(game());

        for (int lvl = 1; lvl <= 9; lvl++) {
            int level = lvl;
            assertThat(specs).anyMatch(s -> ("spell_slot_max:" + level).equals(s.key()));
            assertThat(specs).anyMatch(s -> ("spell_slot_current:" + level).equals(s.key()));
        }
    }

    @Test
    void computedModifiersCalculatesProficiencyBonus() {
        List<SheetAttribute> attrs = List.of(
            attr("level", 5)
        );
        Map<String, Integer> mods = system.computedModifiers(null, attrs);

        assertThat(mods.get("proficiency_bonus")).isEqualTo(3); // 2 + (5-1)/4 = 3
    }

    @Test
    void computedModifiersCalculatesAbilityModifiers() {
        List<SheetAttribute> attrs = List.of(
            attr("ability:strength", 16),
            attr("ability:dexterity", 14),
            attr("ability:constitution", 13),
            attr("ability:intelligence", 10),
            attr("ability:wisdom", 8),
            attr("ability:charisma", 6)
        );
        Map<String, Integer> mods = system.computedModifiers(null, attrs);

        assertThat(mods.get("ability:strength")).isEqualTo(3);  // (16-10)/2 = 3
        assertThat(mods.get("ability:dexterity")).isEqualTo(2); // (14-10)/2 = 2
        assertThat(mods.get("ability:constitution")).isEqualTo(1); // (13-10)/2 = 1
        assertThat(mods.get("ability:intelligence")).isEqualTo(0); // (10-10)/2 = 0
        assertThat(mods.get("ability:wisdom")).isEqualTo(-1); // floor((8-10)/2) = -1
        assertThat(mods.get("ability:charisma")).isEqualTo(-2); // floor((6-10)/2) = -2
    }

    @Test
    void computedModifiersCalculatesSaveModifiersWithProficiency() {
        SheetAttribute strSave = new SheetAttribute();
        strSave.setKey("save:strength");
        strSave.setFlags(1); // proficient

        SheetAttribute dexSave = new SheetAttribute();
        dexSave.setKey("save:dexterity");
        dexSave.setFlags(0); // not proficient

        List<SheetAttribute> attrs = List.of(
            attr("level", 4), // prof bonus = 2
            attr("ability:strength", 16), // mod = 3
            attr("ability:dexterity", 14), // mod = 2
            strSave,
            dexSave
        );

        Map<String, Integer> mods = system.computedModifiers(null, attrs);

        // str save: 3 (mod) + 2 (prof) = 5
        assertThat(mods.get("save:strength")).isEqualTo(5);
        // dex save: 2 (mod) + 0 (no prof) = 2
        assertThat(mods.get("save:dexterity")).isEqualTo(2);
    }

    @Test
    void computedModifiersCalculatesSkillModifiers() {
        SheetAttribute athletics = new SheetAttribute();
        athletics.setKey("skill:athletics");
        athletics.setFlags(DndCharacterSystem.SKILL_FLAG_PROFICIENT); // 1

        SheetAttribute acrobatics = new SheetAttribute();
        acrobatics.setKey("skill:acrobatics");
        acrobatics.setFlags(DndCharacterSystem.SKILL_FLAG_EXPERTISE); // 2

        SheetAttribute stealth = new SheetAttribute();
        stealth.setKey("skill:stealth");
        stealth.setFlags(0); // none

        List<SheetAttribute> attrs = List.of(
            attr("level", 5), // prof = 3
            attr("ability:strength", 16), // mod = 3
            attr("ability:dexterity", 14), // mod = 2
            athletics,
            acrobatics,
            stealth
        );

        Map<String, Integer> mods = system.computedModifiers(null, attrs);

        // athletics (str): 3 + 3 = 6
        assertThat(mods.get("skill:athletics")).isEqualTo(6);
        // acrobatics (dex): 2 + 2*3 = 8
        assertThat(mods.get("skill:acrobatics")).isEqualTo(8);
        // stealth (dex): 2 + 0 = 2
        assertThat(mods.get("skill:stealth")).isEqualTo(2);
    }

    @Test
    void optionsForReturnsClassOptions() {
        List<String> options = system.optionsFor("class");
        assertThat(options).containsExactly(
            "Воин", "Паладин", "Плут", "Следопыт", "Колдун", "Чародей",
            "Волшебник", "Жрец", "Друид", "Бард", "Монах", "Варвар"
        );
    }

    @Test
    void optionsForReturnsRaceOptions() {
        List<String> options = system.optionsFor("race");
        assertThat(options).containsExactly(
            "Человек", "Эльф", "Полуэльф", "Гном", "Полурослик", "Дварф",
            "Полуорк", "Драконорождённый", "Тифлинг"
        );
    }

    @Test
    void optionsForReturnsBackgroundOptions() {
        List<String> options = system.optionsFor("background");
        assertThat(options).containsExactly(
            "Артист", "Беспризорник", "Благородный", "Гильдейский ремесленник", "Моряк",
            "Мудрец", "Народный герой", "Отшельник", "Пират", "Преступник",
            "Прислужник", "Солдат", "Чужеземец", "Шарлатан"
        );
    }

    @Test
    void flagMaxReturnsCorrectValues() {
        assertThat(system.flagMax("save:strength")).isEqualTo(1);
        assertThat(system.flagMax("skill:athletics")).isEqualTo(2);
        assertThat(system.flagMax("ability:strength")).isEqualTo(0);
    }

    @Test
    void otherOptionLabelReturnsCorrectLabels() {
        assertThat(system.otherOptionLabel("class")).isEqualTo("Другой");
        assertThat(system.otherOptionLabel("race")).isEqualTo("Другая");
        assertThat(system.otherOptionLabel("background")).isEqualTo("Другая");
        assertThat(system.otherOptionLabel("unknown")).isEqualTo("");
    }

    @Test
    void moneyIsLimitedFromBelowByZero() {
        for (String key : DndCharacterSystem.MONEY_KINDS) {
            FieldConstraints constraints = system.constraints(key);

            assertThat(constraints).as("ограничения вида монет %s", key).isNotNull();
            assertThat(constraints.min()).as("нижняя граница %s", key).isZero();
            assertThat(constraints.stepOrDefault()).isEqualTo(1);
            assertThat(constraints.manualInputAllowed())
                    .as("ручной ввод денег разрешён").isTrue();
            assertThat(constraints.outOfRange(-1)).as("отрицательное число отклоняется").isTrue();
            assertThat(constraints.outOfRange(0)).isFalse();
            assertThat(constraints.outOfRange(10_000)).isFalse();
        }
    }

    @Test
    void dndNumbersAreChangedByArrowsWithoutManualInput() {
        // Ручной ввод в D&D есть только у денег: остальные числа правятся стрелками.
        List<String> numericKeys = List.of(
                DndCharacterSystem.KEY_LEVEL,
                DndCharacterSystem.KEY_HIT_POINTS,
                DndCharacterSystem.KEY_ARMOR_CLASS,
                DndCharacterSystem.PREFIX_ABILITY + "strength",
                DndCharacterSystem.PREFIX_SAVE + "strength",
                DndCharacterSystem.PREFIX_SKILL + "athletics",
                DndCharacterSystem.PREFIX_SPELL_SLOT_MAX + "1",
                DndCharacterSystem.PREFIX_SPELL_SLOT_CURRENT + "1");

        for (String key : numericKeys) {
            FieldConstraints constraints = system.constraints(key);

            assertThat(constraints).as("ограничения параметра %s", key).isNotNull();
            assertThat(constraints.manualInputAllowed())
                    .as("ручной ввод параметра %s закрыт", key).isFalse();
            assertThat(constraints.stepOrDefault()).isEqualTo(1);
        }
    }

    @Test
    void textAndListParametersKeepUnlimitedInput() {
        for (String key : List.of(DndCharacterSystem.KEY_CLASS, DndCharacterSystem.KEY_RACE,
                DndCharacterSystem.KEY_SPELLS, "feats", "skills")) {
            assertThat(system.constraints(key).manualInputAllowed())
                    .as("ручной ввод параметра %s не ограничен", key).isTrue();
        }
    }

    @Test
    void systemAttributesHaveNoBounds() {
        assertThat(system.constraints("class").min()).isNull();
        assertThat(system.constraints("level").min()).isNull();
    }

    @Test
    void abilityForSkillReturnsCorrectAbility() {
        assertThat(DndCharacterSystem.abilityForSkill("athletics")).isEqualTo("strength");
        assertThat(DndCharacterSystem.abilityForSkill("acrobatics")).isEqualTo("dexterity");
        assertThat(DndCharacterSystem.abilityForSkill("stealth")).isEqualTo("dexterity");
        assertThat(DndCharacterSystem.abilityForSkill("perception")).isEqualTo("wisdom");
        assertThat(DndCharacterSystem.abilityForSkill("performance")).isEqualTo("charisma");
        assertThat(DndCharacterSystem.abilityForSkill("unknown")).isNull();
    }
}