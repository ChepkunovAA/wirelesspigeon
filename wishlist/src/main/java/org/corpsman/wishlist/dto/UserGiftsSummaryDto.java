package org.corpsman.wishlist.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Сводная информация о пользователе, имеющем подарки в вишлисте.
 */
@Getter
@Setter
public class UserGiftsSummaryDto {

    private Long userId;

    private String username;

    private Long giftCount;
}