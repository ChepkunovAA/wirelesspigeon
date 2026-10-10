package org.corpsman.rpg.dto;

import lombok.Data;

/**
 * Комната игры.
 */
@Data
public class RoomDto {

    private Long id;

    private Long gameId;

    private String title;

    private boolean privateRoom;

    /**
     * Доступна ли комната зрителю для входа (иначе показываем «замок»).
     */
    private boolean accessible;
}