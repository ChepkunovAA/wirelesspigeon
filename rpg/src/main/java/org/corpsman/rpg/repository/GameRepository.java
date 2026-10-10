package org.corpsman.rpg.repository;

import org.corpsman.rpg.model.Game;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
     * Видимые игры постранично. Сортировка та же: статус
     * (набор → идёт → завершена), затем свежие сверху.
     */
    @Query(value = """
            SELECT g FROM Game g
            WHERE (:search = '' OR LOWER(g.title) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')))
              AND (
                     g.masterId = :uid
                  OR (g.visibility = 'CLOSED' AND EXISTS (
                      SELECT w FROM GameWhitelistEntry w WHERE w.gameId = g.id AND w.userId = :uid))
                  OR ((:roleIsGuest = true) AND (g.visibility IN ('PUBLIC','GUEST')))
                  OR ((:roleIsGuest = false) AND (g.visibility IN ('PUBLIC','OPEN','GUEST')))
              )
            ORDER BY CASE g.status
                         WHEN 'RECRUITING' THEN 0
                         WHEN 'ACTIVE' THEN 1
                         ELSE 2
                     END ASC, g.createdAt DESC""",
            countQuery = """
            SELECT count(g) FROM Game g
            WHERE (:search = '' OR LOWER(g.title) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')))
              AND (
                     g.masterId = :uid
                  OR (g.visibility = 'CLOSED' AND EXISTS (
                      SELECT w FROM GameWhitelistEntry w WHERE w.gameId = g.id AND w.userId = :uid))
                  OR ((:roleIsGuest = true) AND (g.visibility IN ('PUBLIC','GUEST')))
                  OR ((:roleIsGuest = false) AND (g.visibility IN ('PUBLIC','OPEN','GUEST')))
              )""")
    Page<Game> searchPage(@Param("search") String search,
                          @Param("uid") Long uid,
                          @Param("roleIsGuest") boolean roleIsGuest,
                          Pageable pageable);

/**
     * «Мои игры»: игры, которые зритель ведёт либо в которых участвует
     * (заявка принята). Завершённые игры в раздел не попадают — они остаются
     * в общем списке. Сортировка та же, что у общего списка. Выборка отдельная
     от постраничной, чтобы свои игры не зависели от страницы общего списка.
 */
@Query(value = """
            SELECT g FROM Game g
            WHERE g.status <> 'CLOSED'
              AND (:search = '' OR LOWER(g.title) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')))
              AND (g.masterId = :uid
                   OR EXISTS (SELECT a FROM GameApplication a
                              WHERE a.gameId = g.id AND a.userId = :uid AND a.status = 'ACCEPTED'))
            ORDER BY CASE g.status
                         WHEN 'RECRUITING' THEN 0
                         WHEN 'ACTIVE' THEN 1
                         ELSE 2
                     END ASC, g.createdAt DESC""",
            countQuery = """
            SELECT count(g) FROM Game g
            WHERE g.status <> 'CLOSED'
              AND (:search = '' OR LOWER(g.title) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')))
              AND (g.masterId = :uid
                   OR EXISTS (SELECT a FROM GameApplication a
                              WHERE a.gameId = g.id AND a.userId = :uid AND a.status = 'ACCEPTED'))""")
    Page<Game> ownGamesPage(@Param("search") String search, @Param("uid") Long uid, Pageable pageable);

/**
     * Видимые игры, кроме незавершённых своих: в общем списке свои не
     * повторяются, они вынесены в раздел «Мои игры». Завершённые игры в раздел
     * «Мои игры» не попадают, поэтому здесь они остаются.
 */
@Query(value = """
            SELECT g FROM Game g
            WHERE (:search = '' OR LOWER(g.title) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')))
              AND (
                     g.status = 'CLOSED'
                  OR (g.masterId <> :uid
                      AND NOT EXISTS (SELECT a FROM GameApplication a
                                      WHERE a.gameId = g.id AND a.userId = :uid AND a.status = 'ACCEPTED'))
              )
              AND (
                     (g.visibility = 'CLOSED' AND EXISTS (
                         SELECT w FROM GameWhitelistEntry w WHERE w.gameId = g.id AND w.userId = :uid))
                  OR ((:roleIsGuest = true) AND (g.visibility IN ('PUBLIC','GUEST')))
                  OR ((:roleIsGuest = false) AND (g.visibility IN ('PUBLIC','OPEN','GUEST')))
              )
            ORDER BY CASE g.status
                         WHEN 'RECRUITING' THEN 0
                         WHEN 'ACTIVE' THEN 1
                         ELSE 2
                     END ASC, g.createdAt DESC""",
            countQuery = """
            SELECT count(g) FROM Game g
            WHERE (:search = '' OR LOWER(g.title) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%')))
              AND (
                     g.status = 'CLOSED'
                  OR (g.masterId <> :uid
                      AND NOT EXISTS (SELECT a FROM GameApplication a
                                      WHERE a.gameId = g.id AND a.userId = :uid AND a.status = 'ACCEPTED'))
              )
              AND (
                     (g.visibility = 'CLOSED' AND EXISTS (
                         SELECT w FROM GameWhitelistEntry w WHERE w.gameId = g.id AND w.userId = :uid))
                  OR ((:roleIsGuest = true) AND (g.visibility IN ('PUBLIC','GUEST')))
                  OR ((:roleIsGuest = false) AND (g.visibility IN ('PUBLIC','OPEN','GUEST')))
              )""")
    Page<Game> otherGamesPage(@Param("search") String search,
                              @Param("uid") Long uid,
                              @Param("roleIsGuest") boolean roleIsGuest,
                              Pageable pageable);
}