package org.corpsman.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.corpsman.auth.model.InviteToken;
import org.corpsman.auth.model.User;
import org.corpsman.auth.model.UserRole;
import org.corpsman.auth.repository.UserRepository;
import org.corpsman.common.event.UserRegisteredEvent;
import org.corpsman.common.exception.BusinessException;
import org.corpsman.common.exception.ErrorCode;
import org.corpsman.common.util.ValidationUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Сервис регистрации и аутентификации пользователей.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final TokenService tokenService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Регистрирует нового пользователя по одноразовому ключу.
     *
     * @param rawToken сырой инвайт-ключ
     * @param username имя пользователя
     * @param password пароль
     * @return созданный пользователь
     * @throws BusinessException если ключ невалиден или имя занято
     */
    @Transactional
    public User register(String rawToken, String username, String password) {
        ValidationUtils.validateUsername(username);
        ValidationUtils.validatePassword(password);

        InviteToken token = tokenService.validateToken(rawToken);
        if (userRepository.existsByUsername(username)) {
            throw new BusinessException(ErrorCode.USERNAME_TAKEN);
        }

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(token.isGuestKey() ? UserRole.GUEST : UserRole.RESIDENT);
        user.setActive(true);
        userRepository.save(user);

        tokenService.markTokenUsed(token);
        log.info("Зарегистрирован новый пользователь: {}", username);

        eventPublisher.publishEvent(new UserRegisteredEvent(user.getId(), username, LocalDateTime.now()));
        return user;
    }

    /**
     * Выполняет аутентификацию пользователя.
     *
     * @param username имя пользователя
     * @param password пароль
     * @return объект аутентификации
     * @throws org.springframework.security.core.AuthenticationException если учётные данные неверны
     */
    public Authentication login(String username, String password) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password));
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);
        });
        log.info("Успешный вход в систему: {}", username);
        return authentication;
    }
}