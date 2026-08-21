package org.corpsman.wishlist.repository;

import org.corpsman.wishlist.model.GiftReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий резервирований подарков.
 */
public interface GiftReservationRepository extends JpaRepository<GiftReservation, Long> {

    /**
     * Проверяет, зарезервировал ли пользователь подарок.
     *
     * @param giftId идентификатор подарка
     * @param userId идентификатор пользователя
     * @return true, если резерв существует
     */
    boolean existsByGiftIdAndUserId(Long giftId, Long userId);

    /**
     * Находит резерв пользователя на подарок.
     *
     * @param giftId идентификатор подарка
     * @param userId идентификатор пользователя
     * @return резерв, если существует
     */
    Optional<GiftReservation> findByGiftIdAndUserId(Long giftId, Long userId);

    /**
     * Все резервы на подарок.
     *
     * @param giftId идентификатор подарка
     * @return список резервов
     */
    List<GiftReservation> findByGiftId(Long giftId);

    /**
     * Удаляет все резервы подарка (при удалении подарка).
     *
     * @param giftId идентификатор подарка
     */
    void deleteByGiftId(Long giftId);
}