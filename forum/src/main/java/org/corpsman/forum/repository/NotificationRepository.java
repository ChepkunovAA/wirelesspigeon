package org.corpsman.forum.repository;

import org.corpsman.forum.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Репозиторий уведомлений.
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Все уведомления пользователя (новые сверху).
     *
     * @param userId идентификатор пользователя
     * @return список уведомлений
     */
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Количество непрочитанных уведомлений.
     *
     * @param userId идентификатор пользователя
     * @return количество
     */
    long countByUserIdAndReadFalse(Long userId);

    /**
     * Отмечает все уведомления пользователя прочитанными.
     *
     * @param userId идентификатор пользователя
     */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            "UPDATE Notification n SET n.read = true WHERE n.userId = :userId AND n.read = false")
    void markAllRead(@org.springframework.data.repository.query.Param("userId") Long userId);
}
