package org.corpsman.rpg.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.corpsman.common.model.BaseEntity;

/**
 * RPG-игра.
 * <p>
 * Создатель игры становится её ведущим (master).
 */
@Entity
@Table(name = "rpg_games", indexes = {
        @Index(name = "idx_rpg_games_master", columnList = "master_id"),
        @Index(name = "idx_rpg_games_visibility", columnList = "visibility")
})
@Getter
@Setter
public class Game extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 1000000)
    private String description;

    /**
     * Идентификатор ведущего (создателя игры).
     */
    @Column(name = "master_id", nullable = false)
    private Long masterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GameSystem system = GameSystem.VERBAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GameVisibility visibility = GameVisibility.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GameStatus status = GameStatus.RECRUITING;

    /**
     * Бинарная видимость параметров листов: если true — все, кто видит игру,
     * видят целиком листы персонажей; если false — посторонним видны только
     * стандартные поля (имя и квента). Ведущий и владелец листа видят всё всегда.
     */
    @Column(name = "attributes_visible", nullable = false)
    private boolean attributesVisible = true;
}