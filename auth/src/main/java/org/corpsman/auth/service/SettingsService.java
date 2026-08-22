package org.corpsman.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.corpsman.auth.model.User;
import org.corpsman.auth.model.UserSettings;
import org.corpsman.auth.repository.UserRepository;
import org.corpsman.auth.repository.UserSettingsRepository;
import org.corpsman.common.exception.BusinessException;
import org.corpsman.common.exception.ErrorCode;
import org.corpsman.common.util.PasswordGenerator;
import org.corpsman.common.util.ValidationUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сервис настроек пользователя и смены пароля.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SettingsService {

    private final UserSettingsRepository settingsRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Возвращает настройки пользователя, при первом обращении создаёт значения по умолчанию.
     *
     * @param userId идентификатор пользователя
     * @return настройки пользователя
     */
    @Transactional
    public UserSettings getByUserId(Long userId) {
        return settingsRepository.findById(userId)
                .orElseGet(() -> {
                    UserSettings created = new UserSettings();
                    created.setUserId(userId);
                    created.setDefaultVisibility("OPEN");
                    created.setShowPublicPosts(true);
                    log.debug("Созданы настройки по умолчанию для пользователя {}", userId);
                    return settingsRepository.save(created);
                });
    }

    /**
     * Обновляет настройки пользователя.
     *
     * @param userId              идентификатор пользователя
     * @param threadAnonymous     треды по умолчанию анонимные
     * @param commentAnonymous    комментарии по умолчанию анонимные
     * @param defaultVisibility   видимость новых тредов по умолчанию
     * @param showPublicPosts     показывать публичные посты
     */
    @Transactional
    public void update(Long userId, boolean threadAnonymous, boolean commentAnonymous,
                       String defaultVisibility, boolean showPublicPosts) {
        UserSettings settings = getByUserId(userId);
        settings.setDefaultThreadAnonymous(threadAnonymous);
        settings.setDefaultCommentAnonymous(commentAnonymous);
        settings.setDefaultVisibility(normalizeVisibility(defaultVisibility));
        settings.setShowPublicPosts(showPublicPosts);
        settingsRepository.save(settings);
        log.info("Обновлены настройки пользователя {}", userId);
    }

    /**
     * Меняет пароль пользователя.
     *
     * @param userId      идентификатор пользователя
     * @param oldPassword текущий пароль
     * @param newPassword новый пароль
     * @throws BusinessException если старый пароль неверен или новый не проходит валидацию
     */
    /**
     * Нормализует значение видимости; неизвестное заменяется на OPEN.
     *
     * @param raw сырое значение
     * @return PUBLIC/OPEN/CLOSED
     */
    private String normalizeVisibility(String raw) {
        if (raw == null) {
            return "OPEN";
        }
        return switch (raw.trim().toUpperCase()) {
            case "PUBLIC" -> "PUBLIC";
            case "CLOSED" -> "CLOSED";
            default -> "OPEN";
        };
    }

    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        ValidationUtils.validatePassword(newPassword);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!passwordEncoder.matches(oldPassword == null ? "" : oldPassword, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS, "Текущий пароль указан неверно");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("Пользователь {} сменил пароль", userId);
    }

    /**
     * Сбрасывает пароль пользователя без проверки текущего (для администратора).
     *
     * @param userId      идентификатор пользователя
     * @param newPassword новый пароль
     * @throws BusinessException если новый пароль не проходит валидацию
     */
    @Transactional
    public void resetPassword(Long userId, String newPassword) {
        ValidationUtils.validatePassword(newPassword);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("Администратор сбросил пароль пользователя {}", userId);
    }

    /**
     * Сбрасывает пароль пользователя, автоматически генерируя новый
     * (5 символов: латинские буквы и цифры).
     *
     * @param userId идентификатор пользователя
     * @return сгенерированный сырой пароль для передачи пользователю
     */
    @Transactional
    public String generateAndReset(Long userId) {
        String raw = PasswordGenerator.generate();
        resetPassword(userId, raw);
        return raw;
    }
}
