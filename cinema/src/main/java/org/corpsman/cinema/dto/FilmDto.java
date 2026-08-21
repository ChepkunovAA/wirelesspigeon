package org.corpsman.cinema.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.corpsman.cinema.model.CensorshipRating;
import org.corpsman.cinema.model.FilmStatus;

import java.time.LocalDateTime;

/**
 * Представление фильма.
 */
@Getter
@Setter
public class FilmDto {

    private Long id;

    @NotBlank(message = "Название обязательно")
    @Size(min = 2, max = 200, message = "Название должно быть от 2 до 200 символов")
    private String title;

    @Min(value = 1900, message = "Год не может быть меньше 1900")
    @Max(value = 2100, message = "Год не может быть больше 2100")
    private Integer year;

    @Size(max = 500, message = "Ссылка на трейлер слишком длинная")
    private String trailerUrl;

    private FilmStatus status;

    private LocalDateTime watchedAt;

    private CensorshipRating censorship;

    private Integer userRating;

    private Double averageRating;

    private Long totalVotes;

    private String proposerUsername;

    private Long proposerId;
}