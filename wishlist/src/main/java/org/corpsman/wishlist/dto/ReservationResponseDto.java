package org.corpsman.wishlist.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Ответ после резервирования или отмены резерва (AJAX).
 */
@Getter
@Setter
public class ReservationResponseDto {

    private boolean success;

    private String message;

    private Integer newReservedCount;

    private boolean limitReached;

    private boolean reservedByMe;

    private String buttonText;

    private String buttonClass;
}