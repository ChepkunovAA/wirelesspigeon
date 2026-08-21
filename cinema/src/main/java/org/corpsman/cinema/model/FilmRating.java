package org.corpsman.cinema.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.corpsman.common.model.BaseEntity;

/**
 * Оценка фильма пользователем.
 */
@Entity
@Table(name = "film_ratings",
        uniqueConstraints = @UniqueConstraint(name = "uk_film_rating_user", columnNames = {"film_id", "user_id"}),
        indexes = {
                @Index(name = "idx_film_ratings_film_id", columnList = "film_id"),
                @Index(name = "idx_film_ratings_user_id", columnList = "user_id")
        })
@Getter
@Setter
public class FilmRating extends BaseEntity {

    @Column(name = "film_id", nullable = false)
    private Long filmId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Integer rating;

    @Column(length = 1000)
    private String review;
}