package org.corpsman.cinema.repository;

import org.corpsman.cinema.model.CensorshipRating;
import org.corpsman.cinema.model.Film;
import org.corpsman.cinema.model.FilmStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий фильмов.
 */
public interface FilmRepository extends JpaRepository<Film, Long> {

    /**
     * Находит выбранный фильм для следующего просмотра.
     *
     * @return фильм со статусом SELECTED, если существует
     */
    Optional<Film> findByStatus(FilmStatus status);

    /**
     * Находит последний просмотренный фильм.
     *
     * @param status статус WATCHED
     * @return последний просмотренный фильм
     */
    Optional<Film> findFirstByStatusOrderByWatchedAtDesc(FilmStatus status);

    /**
     * Находит все фильмы по статусу.
     *
     * @param status статус фильма
     * @return список фильмов
     */
    List<Film> findAllByStatus(FilmStatus status);

    /**
     * Находит предложения пользователя.
     *
     * @param userId идентификатор пользователя
     * @param status статус фильма
     * @return список фильмов
     */
    List<Film> findByUserIdAndStatus(Long userId, FilmStatus status);

    long countByUserIdAndStatus(Long userId, FilmStatus status);

    /**
     * Проверяет наличие дубликата предложения пользователя.
     *
     * @param userId идентификатор пользователя
     * @param title  название фильма
     * @param status статус
     * @return true, если такой фильм уже предложен
     */
    boolean existsByUserIdAndTitleIgnoreCaseAndStatus(Long userId, String title, FilmStatus status);

    /**
     * Находит идентификаторы пользователей, имеющих фильмы в заданном статусе.
     *
     * @param status статус фильма
     * @return список идентификаторов пользователей
     */
    @Query("SELECT DISTINCT f.userId FROM Film f WHERE f.status = :status")
    List<Long> findDistinctUserIdsByStatus(@Param("status") FilmStatus status);

    /**
     * Находит просмотренные фильмы с фильтрацией по поиску и цензурному рейтингу.
     *
     * @param status     статус WATCHED
     * @param search     поиск по названию (может быть null)
     * @param censorship цензурный рейтинг (может быть null)
     * @param pageable   пагинация
     * @return страница фильмов
     */
    @Query("SELECT f FROM Film f WHERE f.status = :status "
            + "AND (:search IS NULL OR LOWER(f.title) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) "
            + "AND (:censorship IS NULL OR f.censorship = :censorship)")
    Page<Film> findWatched(@Param("status") FilmStatus status,
                           @Param("search") String search,
                           @Param("censorship") CensorshipRating censorship,
                           Pageable pageable);
}