package org.corpsman.wishlist.repository;

import org.corpsman.wishlist.model.Gift;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Репозиторий подарков.
 */
public interface GiftRepository extends JpaRepository<Gift, Long> {

    /**
     * Все подарки пользователя, от новых к старым.
     *
     * @param ownerId идентификатор владельца
     * @return список подарков
     */
    List<Gift> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    /**
     * Количество подарков у пользователя.
     *
     * @param ownerId идентификатор владельца
     * @return количество подарков
     */
    Long countByOwnerId(Long ownerId);

    /**
     * Идентификаторы пользователей, у которых есть хотя бы один подарок.
     *
     * @return список идентификаторов владельцев
     */
    @Query("SELECT DISTINCT g.ownerId FROM Gift g")
    List<Long> findDistinctOwnerIds();

    /**
     * Атомарное увеличение счётчика резервов с проверкой лимита.
     *
     * @param giftId идентификатор подарка
     * @return количество обновлённых строк (0, если лимит достигнут)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Gift g SET g.reservedCount = g.reservedCount + 1 "
            + "WHERE g.id = :giftId AND (g.limit = 0 OR g.reservedCount < g.limit)")
    int incrementReservedCount(@Param("giftId") Long giftId);

    /**
     * Атомарное уменьшение счётчика резервов.
     *
     * @param giftId идентификатор подарка
     * @return количество обновлённых строк
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Gift g SET g.reservedCount = g.reservedCount - 1 "
            + "WHERE g.id = :giftId AND g.reservedCount > 0")
    int decrementReservedCount(@Param("giftId") Long giftId);
}