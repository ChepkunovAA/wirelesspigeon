package org.corpsman.rpg.repository;

import org.corpsman.rpg.model.Game;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Репозиторий игр.
 */
public interface GameRepository extends JpaRepository<Game, Long> {

    List<Game> findByMasterIdOrderByCreatedAtDesc(Long masterId);

    /**
     * Поиск видимых игр с учётом видимости по ролям.
     * <p>
     * Гость ({@code roleIsGuest=true}) видит PUBLIC и GUEST; резидент/мастер —
     * также OPEN. Закрытые видит только ведущий или белый список. Анонимы до
     * /rpg/** не допускаются (SecurityConfig), поэтому uid всегда непустой.
     *
     * @param search      подстрока заголовка (пустая — пропуск)
     * @param uid         идентификатор зрителя
     * @param roleIsGuest флаг «зритель — гость клуба»
     * @return список видимых игр
     */
    @Query("""
            SELECT g FROM Game g
            WHERE (:search = '' OR LOWER(g.title) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')))
              AND (
                     g.masterId = :uid
                  OR (g.visibility = 'CLOSED' AND EXISTS (
                      SELECT w FROM GameWhitelistEntry w WHERE w.gameId = g.id AND w.userId = :uid))
                  OR ((:roleIsGuest = true) AND (g.visibility IN ('PUBLIC','GUEST')))
                  OR ((:roleIsGuest = false) AND (g.visibility IN ('PUBLIC','OPEN','GUEST')))
              )
            ORDER BY g.createdAt DESC""")
    List<Game> search(@Param("search") String search,
                      @Param("uid") Long uid,
                      @Param("roleIsGuest") boolean roleIsGuest);
}