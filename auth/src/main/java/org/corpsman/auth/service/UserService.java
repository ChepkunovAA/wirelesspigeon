package org.corpsman.auth.service;

import lombok.RequiredArgsConstructor;
import org.corpsman.auth.mapper.UserMapper;
import org.corpsman.auth.model.User;
import org.corpsman.auth.repository.UserRepository;
import org.corpsman.common.dto.UserDto;
import org.corpsman.common.exception.BusinessException;
import org.corpsman.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Сервис управления пользователями.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    /**
     * Возвращает пользователя по идентификатору.
     *
     * @param id идентификатор пользователя
     * @return DTO пользователя
     * @throws BusinessException если пользователь не найден
     */
    @Transactional(readOnly = true)
    public UserDto findById(Long id) {
        return userMapper.toDto(findEntityById(id));
    }

    /**
     * Возвращает пользователя по имени.
     *
     * @param username имя пользователя
     * @return DTO пользователя
     * @throws BusinessException если пользователь не найден
     */
    @Transactional(readOnly = true)
    public UserDto findByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return userMapper.toDto(user);
    }

    /**
     * Устанавливает признак активности пользователя.
     *
     * @param userId идентификатор пользователя
     * @param active признак активности
     */
    @Transactional
    public void setActive(Long userId, boolean active) {
        User user = findEntityById(userId);
        user.setActive(active);
        userRepository.save(user);
    }

    /**
     * Возвращает сущность пользователя, если он существует.
     *
     * @param userId идентификатор пользователя
     * @return пользователь
     */
    public Optional<User> findEntity(Long userId) {
        return userRepository.findById(userId);
    }

    private User findEntityById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}