package org.corpsman.rpg.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Представление листа персонажа с параметрами и расчётными значениями.
 */
@Data
public class CharacterSheetViewDto {

    private Long id;

    private Long gameId;

    private Long ownerId;

    private String ownerName;

    private String name;

    private String avatarPath;

    private String kventa;

    private boolean npc;

    private List<AttributeViewDto> attributes = new ArrayList<>();

    /**
     * Список вспомогательных модификаторов для popup-подстановки в комментарий к броску.
     */
    private List<CharacterModifierDto> modifiers = new ArrayList<>();

    /**
     * Ячейки заклинаний по уровням (текущее/максимальное) для блока «Ячейки».
     * Уровни, у которых и текущее, и максимальное равны нулю, не попадают в список.
     */
    private List<SpellSlotDto> spellSlots = new ArrayList<>();

    /**
     * Заклинания, сгруппированные по уровню (по возрастанию) для блока
     * «Заклинания». Уровни без элементов отсутствуют.
     */
    private List<SpellGroupDto> spellGroups = new ArrayList<>();

    /**
     * Ячейки заклинаний для формы редактирования: текущее и максимальное
     * редактируются рядом в одном блоке «Ячейки».
     */
    private List<SpellSlotEditDto> spellSlotEdits = new ArrayList<>();
}