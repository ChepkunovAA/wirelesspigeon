package org.corpsman.forum.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Представление бана пользователя в треде.
 */
@Getter
@Setter
public class TopicBanDto {

    private Long userId;
    private String username;
    private String bannedBy;
    private String reason;
    private LocalDateTime createdAt;
}