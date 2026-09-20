package org.corpsman.rpg.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.corpsman.common.model.BaseEntity;

/**
 * Заявка на участие в игре.
 * <p>
 * Заявка всегда содержит лист персонажа. До принятия ведущим пользователь
 * не считается игроком. NPC ведущего создаются с авто-принятием.
 */
@Entity
@Table(name = "rpg_applications")
@Getter
@Setter
public class GameApplication extends BaseEntity {

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "sheet_id", nullable = false)
    private Long sheetId;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status = ApplicationStatus.PENDING;

    @Column(name = "reviewed_at")
    private java.time.LocalDateTime reviewedAt;
}