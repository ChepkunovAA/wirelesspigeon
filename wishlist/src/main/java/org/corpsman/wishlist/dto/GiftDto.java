package org.corpsman.wishlist.dto;

import lombok.Getter;
import lombok.Setter;
import org.corpsman.common.dto.UserDto;

import java.util.List;

/**
 * Представление подарка в вишлисте.
 */
@Getter
@Setter
public class GiftDto {

    private Long id;

    private String title;

    private String description;

    private Integer limit;

    private Integer reservedCount;

    private List<UserDto> reservedByUsers;

    private boolean owner;

    private boolean reservedByMe;

    private boolean limitReached;
}