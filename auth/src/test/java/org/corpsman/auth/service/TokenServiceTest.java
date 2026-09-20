package org.corpsman.auth.service;

import org.corpsman.auth.dto.InviteTokenDto;
import org.corpsman.auth.model.InviteToken;
import org.corpsman.auth.model.User;
import org.corpsman.auth.repository.InviteTokenRepository;
import org.corpsman.auth.repository.UserRepository;
import org.corpsman.common.exception.BusinessException;
import org.corpsman.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock
    private InviteTokenRepository tokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private TokenService tokenService;

    @Test
    void generateTokenCreatesTokenWithHashAndSevenDayTtl() {
        when(passwordEncoder.encode(anyString())).thenReturn("bcrypt-hash");
        when(tokenRepository.save(any(InviteToken.class))).thenAnswer(invocation -> {
            InviteToken token = invocation.getArgument(0);
            token.setId(1L);
            return token;
        });

        String raw = tokenService.generateToken(42L);

        assertThat(raw).isNotBlank();
        ArgumentCaptor<InviteToken> captor = ArgumentCaptor.forClass(InviteToken.class);
        verify(tokenRepository).save(captor.capture());
        InviteToken saved = captor.getValue();
        assertThat(saved.getCodeHash()).isEqualTo("bcrypt-hash");
        assertThat(saved.isUsed()).isFalse();
        assertThat(saved.getCreatedBy()).isEqualTo(42L);
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now().plusDays(6));
    }

    @Test
    void validateTokenReturnsTokenForValidRawCode() {
        InviteToken token = token(LocalDateTime.now().plusDays(1), false, "hash");
        when(tokenRepository.findAll()).thenReturn(List.of(token));
        when(passwordEncoder.matches("raw-code", "hash")).thenReturn(true);

        InviteToken result = tokenService.validateToken("raw-code");

        assertThat(result).isSameAs(token);
    }

    @Test
    void validateTokenThrowsInvalidTokenForWrongCode() {
        InviteToken token = token(LocalDateTime.now().plusDays(1), false, "hash");
        when(tokenRepository.findAll()).thenReturn(List.of(token));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> tokenService.validateToken("wrong"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    void validateTokenThrowsInvalidTokenForBlankCode() {
        assertThatThrownBy(() -> tokenService.validateToken("   "))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOKEN);
        verify(tokenRepository, never()).findAll();
    }

    @Test
    void validateTokenThrowsExpiredWhenTokenIsExpired() {
        InviteToken token = token(LocalDateTime.now().minusHours(1), false, "hash");
        when(tokenRepository.findAll()).thenReturn(List.of(token));
        when(passwordEncoder.matches("raw-code", "hash")).thenReturn(true);

        assertThatThrownBy(() -> tokenService.validateToken("raw-code"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TOKEN_EXPIRED);
    }

    @Test
    void validateTokenThrowsAlreadyUsedWhenTokenIsUsed() {
        InviteToken token = token(LocalDateTime.now().plusDays(1), true, "hash");
        when(tokenRepository.findAll()).thenReturn(List.of(token));
        when(passwordEncoder.matches("raw-code", "hash")).thenReturn(true);

        assertThatThrownBy(() -> tokenService.validateToken("raw-code"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TOKEN_ALREADY_USED);
    }

    @Test
    void markTokenUsedMarksTokenAndSaves() {
        InviteToken token = token(LocalDateTime.now().plusDays(1), false, "hash");
        when(tokenRepository.save(any(InviteToken.class))).thenReturn(token);

        tokenService.markTokenUsed(token, 1L);

        assertThat(token.isUsed()).isTrue();
        assertThat(token.getUsedBy()).isEqualTo(1L);
        verify(tokenRepository).save(token);
    }

    @Test
    void getAllTokensResolvesCreatorUsernamesInBatch() {
        User admin = new User();
        admin.setId(7L);
        admin.setUsername("auto_chief");
        InviteToken byAdmin = token(LocalDateTime.now().plusDays(1), false, "hash-1");
        byAdmin.setCreatedBy(7L);
        InviteToken orphan = token(LocalDateTime.now().plusDays(1), false, "hash-2");

        when(tokenRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(byAdmin, orphan));
        when(userRepository.findByIdIn(List.of(7L))).thenReturn(List.of(admin));

        List<InviteTokenDto> result = tokenService.getAllTokens();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCreatedByUsername()).isEqualTo("auto_chief");
        assertThat(result.get(0).getCreatedBy()).isEqualTo(7L);
        assertThat(result.get(0).getCodeHash()).isEqualTo("hash-1");
        assertThat(result.get(1).getCreatedByUsername()).isNull();
        verify(userRepository).findByIdIn(any());
    }

    @Test
    void getAllTokensHandlesEmptyListWithoutUserLookup() {
        when(tokenRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());

        List<InviteTokenDto> result = tokenService.getAllTokens();

        assertThat(result).isEmpty();
        verify(userRepository, never()).findByIdIn(any());
    }

    private InviteToken token(LocalDateTime expiresAt, boolean used, String hash) {
        InviteToken token = new InviteToken();
        token.setId(1L);
        token.setCodeHash(hash);
        token.setExpiresAt(expiresAt);
        token.setUsed(used);
        return token;
    }
}