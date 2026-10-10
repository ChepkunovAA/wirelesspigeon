package org.corpsman.rpg.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.corpsman.common.model.BaseEntity;

/**
 * Комната внутри игры. Подобна треду, но не требует стартового поста.
 * Доступ игроков к комнате настраивается белым списком {@code rpg_room_members}.
 */
@Entity
@Table(name = "rpg_room")
@Getter
@Setter
public class Room extends BaseEntity {

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(nullable = false, length = 200)
    private String title;

    /**
     * Приватная комната: доступ только у ведущего и участников белого списка.
     */
    @Column(name = "is_private", nullable = false)
    private boolean privateRoom;
}