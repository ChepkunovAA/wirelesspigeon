package org.corpsman.rpg.dto;

import lombok.Data;
import org.corpsman.rpg.model.GameStatus;
import org.corpsman.rpg.model.GameSystem;
import org.corpsman.rpg.model.GameVisibility;

/**
 * Модель игры для списка.
 */
@Data
public class GameSummaryDto {

    private Long id;

    private String title;

    private String description;

    private Long masterId;

    private String masterName;

    private GameVisibility visibility;

    private GameSystem system;

    private GameStatus status;

    private int participantCount;
}