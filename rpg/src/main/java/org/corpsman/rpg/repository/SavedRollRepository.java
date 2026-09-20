package org.corpsman.rpg.repository;

import org.corpsman.rpg.model.SavedRoll;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Репозиторий сохранённых комбинаций бросков.
 */
public interface SavedRollRepository extends JpaRepository<SavedRoll, Long> {

    List<SavedRoll> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Личные «Избранные» комбинации пользователя для игры (верхний уровень, вне NPC),
     * включая общие «Быстрые броски» с пустой игрой.
     */
    @Query("select r from SavedRoll r where r.userId =:userId and (r.gameId =:gameId or r.gameId is null) "
            + "and r.sheetId is null order by r.createdAt desc")
    List<SavedRoll> findTopLevelForUserGame(@Param("userId") Long userId, @Param("gameId") Long gameId);

    /**
     * Комбинации пользователя игры, привязанные к конкретному листу (избранное NPC).
     */
    @Query("select r from SavedRoll r where r.userId =:userId and r.gameId =:gameId and r.sheetId =:sheetId "
            + "order by r.createdAt desc")
    List<SavedRoll> findForUserGameAndSheet(@Param("userId") Long userId, @Param("gameId") Long gameId,
                                            @Param("sheetId") Long sheetId);

    /**
     * Все комбинации пользователя в игре (несколько контекстов: личные и по листам).
     */
    @Query("select r from SavedRoll r where r.userId =:userId and r.gameId =:gameId "
            + "and ((:sheetId is null and r.sheetId is null) or (:sheetId is not null and r.sheetId =:sheetId)) "
            + "order by r.createdAt desc")
    List<SavedRoll> findForUserGameAndSheetNullable(@Param("userId") Long userId, @Param("gameId") Long gameId,
                                                    @Param("sheetId") Long sheetId);
}