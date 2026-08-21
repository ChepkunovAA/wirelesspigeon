package org.corpsman.cinema.repository;

import org.corpsman.cinema.model.FilmRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий оценок фильмов.
 */
public interface FilmRatingRepository extends JpaRepository<FilmRating, Long> {

    /**
     * Находит оценку пользователя по фильму.
     *
     * @param filmId идентификатор фильма
     * @param userId идентификатор пользователя
     * @return оценка, если существует
     */
    Optional<FilmRating> findByFilmIdAndUserId(Long filmId, Long userId);

    /**
     * Находит все оценки фильма.
     *
     * @param filmId идентификатор фильма
     * @return список оценок
     */
    List<FilmRating> findByFilmId(Long filmId);

    /**
     * Удаляет оценку пользователя по фильму.
     *
     * @param filmId идентификатор фильма
     * @param userId идентификатор пользователя
     */
    void deleteByFilmIdAndUserId(Long filmId, Long userId);

    /**
     * Средняя оценка просмотренных фильмов, предложенных пользователем.
     *
     * @param userId идентификатор пользователя
     * @return средняя оценка или null, если оценок нет
     */
    @Query("SELECT AVG(r.rating) FROM FilmRating r "
            + "WHERE r.filmId IN (SELECT f.id FROM Film f "
            + "WHERE f.userId = :userId AND f.status = org.corpsman.cinema.model.FilmStatus.WATCHED)")
    Double averageRatingForUserFilms(@Param("userId") Long userId);

    /**
     * Средняя оценка, поставленная одним пользователем фильмам другого пользователя.
     *
     * @param raterId пользователь, ставивший оценки
     * @param ownerId владелец фильмов
     * @return средняя оценка или null, если оценок нет
     */
    @Query("SELECT AVG(r.rating) FROM FilmRating r "
            + "WHERE r.userId = :raterId "
            + "AND r.filmId IN (SELECT f.id FROM Film f WHERE f.userId = :ownerId)")
    Double averageRatingByUserOnUserFilms(@Param("raterId") Long raterId, @Param("ownerId") Long ownerId);
}