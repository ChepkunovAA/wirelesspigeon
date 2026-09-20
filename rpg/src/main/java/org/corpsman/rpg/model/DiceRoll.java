package org.corpsman.rpg.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.corpsman.common.model.BaseEntity;

/**
 * Бросок игральных костей, прикреплённый к посту комнаты (один или несколько).
 */
@Entity
@Table(name = "rpg_dice_rolls")
@Getter
@Setter
public class DiceRoll extends BaseEntity {

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "die_size", nullable = false)
    private int dieSize;

    @Column(nullable = false)
    private int count = 1;

    @Column(nullable = false)
    private int modifier;

    @Column(length = 500)
    private String comment;

    /**
     * JSON-массив результатов отдельных костей.
     */
    @Column(length = 1000000)
    private String results;

    /**
     * Сумма результатов + модификатор.
     */
    @Column(nullable = false)
    private int total;
}