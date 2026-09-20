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
}