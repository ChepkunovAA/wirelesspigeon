package org.corpsman.auth.repository;

import org.corpsman.auth.model.InviteToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Репозиторий инвайт-ключей.
 */
public interface InviteTokenRepository extends JpaRepository<InviteToken, Long> {

    /**
     * Находит все ключи, отсортированные по дате создания (новые сверху).
     *
     * @return список ключей
     */
    List<InviteToken> findAllByOrderByCreatedAtDesc();
}