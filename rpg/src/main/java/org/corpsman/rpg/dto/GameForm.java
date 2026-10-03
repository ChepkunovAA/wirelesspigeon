package org.corpsman.rpg.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.corpsman.rpg.model.GameStatus;
import org.corpsman.rpg.model.GameSystem;
import org.corpsman.rpg.model.GameVisibility;

import java.util.List;

/**
 * Форма создания/редактирования игры.
 */
@Data
public class GameForm {

    @NotBlank
    @Size(max = 200)
    private String title;

    @NotBlank
    @Size(max = 1000000)
    private String description;

    private GameSystem system = GameSystem.VERBAL;

    /**
     * ID кастомной системы из библиотеки (если выбрана CUSTOM).
     */
    private Long customSystemId;

    private GameVisibility visibility = GameVisibility.OPEN;

    private GameStatus status = GameStatus.RECRUITING;

    /** «Открытая игра, доступная гостям» (OPEN + галочка ⇒ GUEST). */
    private boolean guestAccess;

    /**
     * Участники видят все параметры листа друг друга; иначе — только имя и квенту.
     */
    private boolean attributesVisible = true;

    /** Белый список закрытой игры (ID пользователей). */
    private List<Long> whitelistItems;
}