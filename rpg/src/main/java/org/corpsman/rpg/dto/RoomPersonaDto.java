package org.corpsman.rpg.dto;

import lombok.Data;

import java.util.List;

/**
 * Персона постинга в комнате: «сам» (игрок/мастер) или NPC (для мастера).
 * Каждая персона несёт собственный набор проверок-модификаторов.
 */
@Data
public class RoomPersonaDto {

    private String id;

    private String name;

    private boolean npc;

    private List<CharacterModifierDto> modifiers = List.of();
}