package org.corpsman.rpg.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Ввод комнаты игры.
 */
@Data
public class RoomForm {

    @NotBlank
    @Size(max = 200)
    private String title;

    private boolean privateRoom;
}