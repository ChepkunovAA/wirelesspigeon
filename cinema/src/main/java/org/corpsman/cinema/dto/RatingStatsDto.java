package org.corpsman.cinema.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Статистика оценок фильма.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RatingStatsDto {

    private double average;
    private long totalVotes;
    private Map<Integer, Long> distribution = new LinkedHashMap<>();
}