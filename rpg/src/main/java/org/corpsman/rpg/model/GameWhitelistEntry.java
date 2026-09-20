package org.corpsman.rpg.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.corpsman.common.model.BaseEntity;

/**
 * Участник белого списка игры (для закрытых игр).
 */
@Entity
@Table(name = "rpg_game_whitelist",
        uniqueConstraints = @UniqueConstraint(name = "uk_rpg_game_whitelist", columnNames = {"game_id", "user_id"}))
@Getter
@Setter
public class GameWhitelistEntry extends BaseEntity {

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "user_id", nullable = false)
    private Long userId;
}