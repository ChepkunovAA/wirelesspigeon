package org.corpsman.cinema.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.corpsman.common.model.BaseEntity;

import java.time.LocalDateTime;

/**
 * Фильм в локальном кинотеатре.
 */
@Entity
@Table(name = "films", indexes = {
        @Index(name = "idx_films_status", columnList = "status"),
        @Index(name = "idx_films_user_id", columnList = "user_id"),
        @Index(name = "idx_films_watched_at", columnList = "watched_at")
})
@Getter
@Setter
public class Film extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "release_year")
    private Integer year;

    @Column(name = "trailer_url", length = 500)
    private String trailerUrl;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FilmStatus status = FilmStatus.PROPOSED;

    @Column(name = "watched_at")
    private LocalDateTime watchedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CensorshipRating censorship;
}