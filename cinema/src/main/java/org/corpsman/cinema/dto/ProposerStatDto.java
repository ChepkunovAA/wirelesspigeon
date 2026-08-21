package org.corpsman.cinema.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Статистика предлагающего пользователя: качество предложений и испорченность.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProposerStatDto {

    private Long userId;

    private String username;

    private Double averageRating;

    private long watchedCount;

    private Double corruption;
}