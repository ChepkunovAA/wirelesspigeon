package org.corpsman.rpg.dto;

import lombok.Data;
import org.corpsman.rpg.model.GameStatus;
import org.corpsman.rpg.model.GameSystem;
import org.corpsman.rpg.model.GameVisibility;

import java.util.ArrayList;
import java.util.List;

/**
 * Модель страницы игры.
 */
@Data
public class GameDetailDto {

    private Long id;

    private String title;

    private String description;

    private Long masterId;

    private String masterName;

    private GameVisibility visibility;

    private GameSystem system;

    private GameStatus status;

    /**
     * Зритель — ведущий игры.
     */
    private boolean masterViewer;

    /**
     * Статус заявки зрителя (null — заявки нет).
     */
    private String viewerApplication;

    private List<RoomDto> rooms = new ArrayList<>();

    private List<String> whitelistNames = new ArrayList<>();

    private List<CharacterSheetViewDto> players = new ArrayList<>();

    private List<CharacterSheetViewDto> npcs = new ArrayList<>();

    private List<GameApplicationDto> applications = new ArrayList<>();
}