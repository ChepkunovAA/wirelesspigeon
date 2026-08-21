package org.corpsman.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.corpsman.auth.model.InviteToken;
import org.corpsman.auth.repository.InviteTokenRepository;
import org.corpsman.common.exception.BusinessException;
import org.corpsman.common.exception.ErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Сервис одноразовых ключей регистрации.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenService {

    private static final int TTL_DAYS = 7;

    private final InviteTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Генерирует новый одноразовый ключ регистрации.
     *
     * @param createdBy идентификатор администратора, создавшего ключ
     * @return сырой ключ для передачи администратору
     */
    public String generateToken(Long createdBy) {
        String raw = UUID.randomUUID().toString();
        InviteToken token = new InviteToken();
        token.setCodeHash(passwordEncoder.encode(raw));
        token.setExpiresAt(LocalDateTime.now().plusDays(TTL_DAYS));
        token.setUsed(false);
        token.setCreatedBy(createdBy);
        tokenRepository.save(token);
        log.info("Сгенерирован ключ регистрации, id={}, создан пользователем={}", token.getId(), createdBy);
        return raw;
    }

    /**
     * Проверяет ключ на валидность и возвращает найденный ключ.
     *
     * @param rawToken сырой ключ
     * @return валидный ключ
     * @throws BusinessException если ключ неверный, просрочен или уже использован
     */
    public InviteToken validateToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        InviteToken token = findByRawToken(rawToken);
        if (token == null) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        if (token.isUsed()) {
            throw new BusinessException(ErrorCode.TOKEN_ALREADY_USED);
        }
        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        }
        return token;
    }

    /**
     * Помечает ключ как использованный.
     *
     * @param token ключ регистрации
     */
    public void markTokenUsed(InviteToken token) {
        token.setUsed(true);
        tokenRepository.save(token);
        log.debug("Ключ регистрации id={} отмечен как использованный", token.getId());
    }

    /**
     * Возвращает все ключи регистрации (новые сверху).
     *
     * @return список ключей
     */
    public List<InviteToken> getAllTokens() {
        return tokenRepository.findAllByOrderByCreatedAtDesc();
    }

    private InviteToken findByRawToken(String rawToken) {
        return tokenRepository.findAll().stream()
                .filter(token -> passwordEncoder.matches(rawToken, token.getCodeHash()))
                .findFirst()
                .orElse(null);
    }
}