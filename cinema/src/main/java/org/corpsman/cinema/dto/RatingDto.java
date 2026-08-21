package org.corpsman.cinema.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Оценка фильма пользователем (вход и ответ AJAX).
 */
@Getter
@Setter
public class RatingDto {

    private Long filmId;

    @NotNull(message = "Оценка обязательна")
    @Min(value = 0, message = "Оценка от 0 до 3")
    @Max(value = 3, message = "Оценка от 0 до 3")
    private Integer rating;

    @Size(max = 1000, message = "Отзыв слишком длинный")
    private String review;

    private boolean userVoted;
    private double average;
    private long totalVotes;
}