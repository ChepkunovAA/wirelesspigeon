package org.corpsman.rpg.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Страница комнаты: посты с бросками и участники (для мастера).
 */
@Data
public class RoomDetailDto {

    private Long id;

    private Long gameId;

    private String title;

    private boolean privateRoom;

    private boolean masterViewer;

    private boolean canPost;

    private Long masterId;

    private List<RoomPostDto> posts = new ArrayList<>();

    private List<Long> memberIds = new ArrayList<>();

    private List<String> usernames = new ArrayList<>();
}