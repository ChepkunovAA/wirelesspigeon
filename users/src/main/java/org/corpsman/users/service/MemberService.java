package org.corpsman.users.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.corpsman.auth.model.User;
import org.corpsman.auth.model.UserRole;
import org.corpsman.auth.repository.UserRepository;
import org.corpsman.common.event.UserDeletedEvent;
import org.corpsman.common.exception.BusinessException;
import org.corpsman.common.exception.ErrorCode;
import org.corpsman.users.dto.MemberDto;
import org.corpsman.users.mapper.MemberMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * Сервис списка участников и бана пользователей.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MemberService {

    private final UserRepository userRepository;
    private final MemberMapper memberMapper;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Возвращает страницу активных участников с поиском по нику.
     *
     * @param search   поиск по нику (case-insensitive, частичное совпадение; может быть пустым)
     * @param pageable пагинация и сортировка
     * @return страница участников
     */
    @Transactional(readOnly = true)
    public Page<MemberDto> getMembers(String search, Pageable pageable) {
        Page<User> users = StringUtils.hasText(search)
                ? userRepository.findByActiveTrueAndUsernameContainingIgnoreCase(search.trim(), pageable)
                : userRepository.findByActiveTrue(pageable);
        return users.map(memberMapper::toDto);
    }

    /**
     * Повышает гостя до резидента.
     *
     * @param userId идентификатор пользователя
     * @throws BusinessException если пользователь не найден или уже не гость
     */
    @Transactional
    public void promoteToResident(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.getRole() != UserRole.GUEST) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED,
                    "Повысить можно только гостя");
        }
        user.setRole(UserRole.RESIDENT);
        userRepository.save(user);
        log.info("Гость {} повышен до резидента", userId);
    }

    /**
     * Повышает резидента до мастера.
     *
     * @param userId идентификатор пользователя
     * @throws BusinessException если пользователь не найден или не резидент
     */
    @Transactional
    public void promoteToMaster(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.getRole() != UserRole.RESIDENT) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED,
                    "Повысить до мастера можно только резидента");
        }
        user.setRole(UserRole.MASTER);
        userRepository.save(user);
        log.info("Резидент {} повышен до мастера", userId);
    }

    /**
     * Понижает резидента до гостя.
     *
     * @param userId идентификатор пользователя
     * @throws BusinessException если пользователь не найден или не резидент
     */
    @Transactional
    public void demoteToGuest(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.getRole() != UserRole.RESIDENT) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED,
                    "Понизить до гостя можно только резидента");
        }
        user.setRole(UserRole.GUEST);
        userRepository.save(user);
        log.info("Резидент {} понижен до гостя", userId);
    }

    /**
     * Банит пользователя (удаляет с сайта).
     * <p>
     * Пользователь деактивируется и больше не отображается на сайте,
     * публикуется событие {@link UserDeletedEvent} для очистки данных в других модулях.
     *
     * @param userId  идентификатор банимого пользователя
     * @param adminId идентификатор администратора
     * @throws BusinessException если пользователь не найден, это сам администратор или другой администратор
     */
    @Transactional
    public void banUser(Long userId, Long adminId) {
        if (userId.equals(adminId)) {
            throw new BusinessException(ErrorCode.CANNOT_BAN_SELF);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (user.getRole() == UserRole.MASTER) {
            throw new BusinessException(ErrorCode.CANNOT_BAN_ADMIN);
        }

        user.setActive(false);
        userRepository.save(user);

        eventPublisher.publishEvent(new UserDeletedEvent(user.getId(), user.getUsername(), LocalDateTime.now()));
        log.info("Администратор {} забанил пользователя {} (ID: {})", adminId, user.getUsername(), userId);
    }
}
