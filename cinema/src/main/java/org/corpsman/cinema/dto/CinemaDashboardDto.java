package org.corpsman.cinema.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Представление главной страницы кинотеатра.
 */
@Getter
@Setter
public class CinemaDashboardDto {

    private FilmDto nextFilm;
    private FilmDto lastFilm;
    private RatingStatsDto lastFilmStats;
}