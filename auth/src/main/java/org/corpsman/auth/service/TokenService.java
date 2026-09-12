package org.corpsman.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.corpsman.auth.dto.InviteTokenDto;
import org.corpsman.auth.model.InviteToken;
import org.corpsman.auth.model.User;
import org.corpsman.auth.model.UserRole;
import org.corpsman.auth.model.User;
import org.corpsman.auth.repository.InviteTokenRepository;
import org.corpsman.auth.repository.UserRepository;
import org.corpsman.common.exception.BusinessException;
import org.corpsman.common.exception.ErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Сервис одноразовых ключей регистрации.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenService {

    private static final int TTL_DAYS = 7;

    private final InviteTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Генерирует новый одноразовый ключ регистрации.
     *
     * @param createdBy идентификатор администратора, создавшего ключ
     * @return сырой ключ для передачи администратору
     */
    public String generateToken(Long createdBy) {
        return generateToken(createdBy, false);
    }

    /**
     * Генерирует ключ регистрации; guestKey=true создаёт ключ гостя.
     *
     * @param createdBy идентификатор создателя
     * @param guestKey  гостевой ключ (новый пользователь получит роль GUEST)
     * @return сырой ключ
     */
    public String generateToken(Long createdBy, boolean guestKey) {
        String raw = UUID.randomUUID().toString();
        InviteToken token = new InviteToken();
        token.setCodeHash(passwordEncoder.encode(raw));
        token.setExpiresAt(LocalDateTime.now().plusDays(TTL_DAYS));
        token.setUsed(false);
        token.setGuestKey(guestKey);
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
     * @param token  ключ регистрации
     * @param usedBy идентификатор пользователя, использовавшего ключ
     */
    public void markTokenUsed(InviteToken token, Long usedBy) {
        token.setUsed(true);
        token.setUsedBy(usedBy);
        tokenRepository.save(token);
        log.debug("Ключ регистрации id={} отмечен как использованный пользователем={}", token.getId(), usedBy);
    }

    /**
     * Возвращает все ключи регистрации (новые сверху) с никами создавшего администратора.
     *
     * @return список DTO ключей
     */
    @Transactional(readOnly = true)
    public List<InviteTokenDto> getAllTokens() {
        return getTokensFor(UserRole.MASTER, null);
    }

    /**
     * Фильтрует ключи по списку администраторов и статусу.
     *
     * @param creators список ников администраторов (пустой = все)
     * @param status   статус: active/used/expired (null = все)
     * @return список DTO ключей
     */
    @Transactional(readOnly = true)
    public List<InviteTokenDto> getTokensFiltered(List<String> creators, String status) {
        List<InviteToken> tokens = tokenRepository.findAllByOrderByCreatedAtDesc();

        // Фильтр по создателю
        if (creators != null && !creators.isEmpty()) {
            List<Long> creatorIds = userRepository.findByUsernameIn(creators).stream()
                    .map(User::getId).toList();
            if (creatorIds.isEmpty()) {
                return List.of();
            }
            tokens = tokens.stream()
                    .filter(t -> creatorIds.contains(t.getCreatedBy()))
                    .toList();
        }

        // Фильтр по статусу
        if (status != null && !status.isBlank()) {
            LocalDateTime now = LocalDateTime.now();
            tokens = switch (status.toLowerCase()) {
                case "active" -> tokens.stream()
                        .filter(t -> !t.isUsed() && t.getExpiresAt().isAfter(now))
                        .toList();
                case "used" -> tokens.stream()
                        .filter(InviteToken::isUsed)
                        .toList();
                case "expired" -> tokens.stream()
                        .filter(t -> !t.isUsed() && t.getExpiresAt().isBefore(now))
                        .toList();
                default -> tokens;
            };
        }

        List<Long> creatorIds = tokens.stream().map(InviteToken::getCreatedBy).filter(Objects::nonNull).distinct().toList();
        List<Long> usedByIds = tokens.stream().map(InviteToken::getUsedBy).filter(Objects::nonNull).distinct().toList();
        List<Long> allIds = new ArrayList<>(creatorIds);
        allIds.addAll(usedByIds);
        allIds = allIds.stream().distinct().toList();
        Map<Long, String> names = allIds.isEmpty() ? Map.of()
                : userRepository.findByIdIn(allIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getUsername));
        return tokens.stream().map(tok -> toDto(tok, names)).toList();
    }

    /**
     * Поиск ключей по нику создавшего администратора (только для MASTER).
     *
     * @param creatorUsername ник администратора
     * @return список DTO ключей
     */
    @Transactional(readOnly = true)
    public List<InviteTokenDto> getTokensForCreator(String creatorUsername) {
        User creator = userRepository.findByUsername(creatorUsername)
                .orElse(null);
        if (creator == null) {
            return List.of();
        }
        List<InviteToken> tokens = tokenRepository.findAllByCreatedByOrderByCreatedAtDesc(creator.getId());
        List<Long> creatorIds = tokens.stream().map(InviteToken::getCreatedBy).filter(Objects::nonNull).distinct().toList();
        List<Long> usedByIds = tokens.stream().map(InviteToken::getUsedBy).filter(Objects::nonNull).distinct().toList();
        List<Long> allIds = new ArrayList<>(creatorIds);
        allIds.addAll(usedByIds);
        allIds = allIds.stream().distinct().toList();
        Map<Long, String> names = allIds.isEmpty() ? Map.of()
                : userRepository.findByIdIn(allIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getUsername));
        return tokens.stream().map(tok -> toDto(tok, names)).toList();
    }

    /**
     * Ключи для страницы /keys: MASTER видит все, RESIDENT — только свои.
     *
     * @param role      роль запрашивающего
     * @param userId    идентификатор запрашивающего
     * @return список DTO ключей
     */
    @Transactional(readOnly = true)
    public List<InviteTokenDto> getTokensFor(UserRole role, Long userId) {
        List<InviteToken> tokens = role == UserRole.RESIDENT
                ? tokenRepository.findAllByCreatedByOrderByCreatedAtDesc(userId).stream()
                        .filter(InviteToken::isGuestKey).toList()
                : tokenRepository.findAllByOrderByCreatedAtDesc();
        List<Long> creatorIds = tokens.stream().map(InviteToken::getCreatedBy).filter(Objects::nonNull).distinct().toList();
        List<Long> usedByIds = tokens.stream().map(InviteToken::getUsedBy).filter(Objects::nonNull).distinct().toList();
        List<Long> allIds = new ArrayList<>(creatorIds);
        allIds.addAll(usedByIds);
        allIds = allIds.stream().distinct().toList();
        Map<Long, String> names = allIds.isEmpty() ? Map.of()
                : userRepository.findByIdIn(allIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getUsername));
        return tokens.stream().map(tok -> toDto(tok, names)).toList();
    }



    /**
     * Возвращает список уникальных ников администраторов, создавших ключи.
     *
     * @return список ников
     */
    @Transactional(readOnly = true)
    public List<String> getAllCreators() {
        return tokenRepository.findAll().stream()
                .map(InviteToken::getCreatedBy)
                .filter(Objects::nonNull)
                .distinct()
                .map(userRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(User::getUsername)
                .sorted()
                .toList();
    }

    private InviteTokenDto toDto(InviteToken token, Map<Long, String> usernamesById) {
        InviteTokenDto dto = new InviteTokenDto();
        dto.setId(token.getId());
        dto.setCodeHash(token.getCodeHash());
        dto.setExpiresAt(token.getExpiresAt());
        dto.setUsed(token.isUsed());
        dto.setGuestKey(token.isGuestKey());
        dto.setCreatedBy(token.getCreatedBy());
        dto.setCreatedAt(token.getCreatedAt());
        if (token.getCreatedBy() != null) {
            dto.setCreatedByUsername(usernamesById.get(token.getCreatedBy()));
        }
        if (token.getUsedBy() != null) {
            dto.setUsedBy(token.getUsedBy());
            dto.setUsedByUsername(usernamesById.get(token.getUsedBy()));
        }
        return dto;
    }

    private InviteToken findByRawToken(String rawToken) {
        return tokenRepository.findAll().stream()
                .filter(token -> passwordEncoder.matches(rawToken, token.getCodeHash()))
                .findFirst()
                .orElse(null);
    }
}