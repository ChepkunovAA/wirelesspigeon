package org.corpsman.rpg.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Пост в комнате.
 */
@Data
public class RoomPostDto {

    private Long id;

    private Long authorId;

    private String authorName;

    private String avatarPath;

    private boolean masterPost;

    private String content;

    private LocalDateTime createdAt;

    private List<DiceRollDto> rolls = List.of();

    private List<RoomAttachmentDto> attachments = List.of();
}