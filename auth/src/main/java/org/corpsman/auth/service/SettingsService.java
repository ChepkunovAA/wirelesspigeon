package org.corpsman.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.corpsman.auth.model.User;
import org.corpsman.auth.model.UserSettings;
import org.corpsman.auth.repository.UserRepository;
import org.corpsman.auth.repository.UserSettingsRepository;
import org.corpsman.common.exception.BusinessException;
import org.corpsman.common.exception.ErrorCode;
import org.corpsman.common.service.FileStorageService;
import org.corpsman.common.util.PasswordGenerator;
import org.corpsman.common.util.ValidationUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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
    private final FileStorageService fileStorageService;

    private static final List<String> ALLOWED_IMAGE_TYPES = List.of("image/jpeg", "image/png", "image/gif", "image/webp");
    private static final long MAX_AVATAR_SIZE = 2 * 1024 * 1024; // 2 MB

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
                    created.setDefaultReadOnly(false);
                    created.setHideGuestThreads(false);
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
     * @param defaultReadOnly     новые треды только для чтения
     * @param showPublicPosts     показывать публичные посты
     * @param hideGuestThreads    скрывать гостевые треды в списке форума
     */
    @Transactional
    public void update(Long userId, boolean threadAnonymous, boolean commentAnonymous,
                       String defaultVisibility, boolean defaultReadOnly,
                       boolean showPublicPosts, boolean hideGuestThreads) {
        UserSettings settings = getByUserId(userId);
        settings.setDefaultThreadAnonymous(threadAnonymous);
        settings.setDefaultCommentAnonymous(commentAnonymous);
        settings.setDefaultVisibility(normalizeVisibility(defaultVisibility));
        settings.setDefaultReadOnly(defaultReadOnly);
        settings.setShowPublicPosts(showPublicPosts);
        settings.setHideGuestThreads(hideGuestThreads);
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
     * @return PUBLIC/OPEN/GUEST/CLOSED
     */
    private String normalizeVisibility(String raw) {
        if (raw == null) {
            return "OPEN";
        }
        return switch (raw.trim().toUpperCase()) {
            case "PUBLIC" -> "PUBLIC";
            case "GUEST" -> "GUEST";
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

    /**
     * Загружает аватарку пользователя.
     *
     * @param userId идентификатор пользователя
     * @param file   загружаемый файл
     * @return путь к сохранённому файлу
     * @throws BusinessException если файл неверного формата или слишком большой
     */
    @Transactional
    public String uploadAvatar(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR, "Файл не выбран");
        }
        if (!ALLOWED_IMAGE_TYPES.contains(file.getContentType())) {
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR,
                    "Неподдерживаемый формат. Допустимы: JPEG, PNG, GIF, WebP");
        }
        if (file.getSize() > MAX_AVATAR_SIZE) {
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR,
                    "Файл слишком большой. Максимум 2 МБ");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // Удаляем старую аватарку, если есть
        if (user.getAvatarPath() != null) {
            try {
                fileStorageService.delete(user.getAvatarPath());
            } catch (Exception e) {
                log.warn("Не удалось удалить старую аватарку пользователя {}: {}", userId, e.getMessage());
            }
        }

        String stored = fileStorageService.store(file);

        user.setAvatarPath(stored);
        userRepository.save(user);
        log.info("Аватарка пользователя {} обновлена: {}", userId, stored);
        return stored;
    }

    /**
     * Удаляет аватарку пользователя.
     *
     * @param userId идентификатор пользователя
     * @throws BusinessException если пользователь не найден
     */
    @Transactional
    public void deleteAvatar(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.getAvatarPath() != null) {
            fileStorageService.delete(user.getAvatarPath());
            user.setAvatarPath(null);
            userRepository.save(user);
            log.info("Аватарка пользователя {} удалена", userId);
        }
    }
}
