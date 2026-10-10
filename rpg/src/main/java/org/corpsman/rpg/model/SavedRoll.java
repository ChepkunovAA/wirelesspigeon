package org.corpsman.rpg.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.corpsman.common.model.BaseEntity;

/**
 * Сохранённая пользователем частая комбинация броска костей.
 */
@Entity
@Table(name = "rpg_saved_rolls")
@Getter
@Setter
public class SavedRoll extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * Игра, для которой сохранена комбинация (null — общие «Быстрые броски»).
     */
    @Column(name = "game_id")
    private Long gameId;

    /**
     * Лист, к которому привязана комбинация (избранное мастера для NPC).
     * null — личное «Избранное» пользователя (верхний уровень).
     */
    @Column(name = "sheet_id")
    private Long sheetId;

    @Column(name = "die_size", nullable = false)
    private int dieSize;

    @Column(nullable = false)
    private int count = 1;

    @Column(nullable = false)
    private int modifier;

    @Column(length = 500)
    private String comment;
}