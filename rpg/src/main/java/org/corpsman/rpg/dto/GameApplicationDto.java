package org.corpsman.rpg.dto;

import lombok.Data;
import org.corpsman.rpg.model.ApplicationStatus;

import java.time.LocalDateTime;

/**
 * Заявка на участие в игре.
 */
@Data
public class GameApplicationDto {

    private Long id;

    private Long gameId;

    private Long userId;

    private String username;

    private Long sheetId;

    private String sheetName;

    private ApplicationStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime reviewedAt;
}