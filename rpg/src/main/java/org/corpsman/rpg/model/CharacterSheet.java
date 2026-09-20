package org.corpsman.rpg.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.corpsman.common.model.BaseEntity;

/**
 * Лист персонажа.
 * <p>
 * Всегда содержит имя, аватарку, квенту и инвентарь (LIST-атрибут «inventory»).
 * Дополнительные параметры зависят от ролевой системы игры.
 */
@Entity
@Table(name = "rpg_character_sheets")
@Getter
@Setter
public class CharacterSheet extends BaseEntity {

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    /**
     * Владелец листа (игрок или ведущий, создающий NPC).
     */
    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false, length = 200)
    private String name;

    /**
     * Аватарка персонажа (путь в uploads/).
     */
    @Column(name = "avatar_path", length = 255)
    private String avatarPath;

    /**
     * Квента — текстовое описание персонажа.
     */
    @Column(length = 1000000)
    private String kventa;

    /**
     * NPC (персонаж ведущего). Создаётся по тем же правилам, заявка авто-принимается.
     */
    @Column(name = "is_npc", nullable = false)
    private boolean npc;
}