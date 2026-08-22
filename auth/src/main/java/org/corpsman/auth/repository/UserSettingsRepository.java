package org.corpsman.auth.repository;

import org.corpsman.auth.model.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Репозиторий настроек пользователей.
 */
public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {
}
