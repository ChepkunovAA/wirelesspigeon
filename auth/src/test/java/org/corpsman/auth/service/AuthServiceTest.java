package org.corpsman.auth.service;

import org.corpsman.auth.model.InviteToken;
import org.corpsman.auth.model.User;
import org.corpsman.auth.model.UserRole;
import org.corpsman.auth.repository.UserRepository;
import org.corpsman.common.event.UserRegisteredEvent;
import org.corpsman.common.exception.BusinessException;
import org.corpsman.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.anyLong;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private TokenService tokenService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AuthService authService;

    @Test
    void registerCreatesUserAndMarksTokenUsed() {
        InviteToken token = validToken();
        when(tokenService.validateToken("raw-token")).thenReturn(token);
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encrypted");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });

        User result = authService.register("raw-token", "alice", "password123");

        assertThat(result.getId()).isEqualTo(1L);
        verify(userRepository).save(any(User.class));
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("alice");
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("encrypted");
        assertThat(captor.getValue().getRole()).isEqualTo(UserRole.RESIDENT);
        assertThat(captor.getValue().isActive()).isTrue();
        verify(tokenService).markTokenUsed(token, 1L);
        verify(eventPublisher).publishEvent(any(UserRegisteredEvent.class));
    }

    @Test
    void registerThrowsUsernameTakenWhenUsernameExists() {
        InviteToken token = validToken();
        when(tokenService.validateToken("raw-token")).thenReturn(token);
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register("raw-token", "alice", "password123"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USERNAME_TAKEN);
        verify(userRepository, org.mockito.Mockito.never()).save(any(User.class));
        verify(tokenService, org.mockito.Mockito.never()).markTokenUsed(any(InviteToken.class), anyLong());
    }

    @Test
    void registerThrowsOnInvalidToken() {
        when(tokenService.validateToken("bad-token"))
                .thenThrow(new BusinessException(ErrorCode.INVALID_TOKEN));

        assertThatThrownBy(() -> authService.register("bad-token", "alice", "password123"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOKEN);
        verify(userRepository, org.mockito.Mockito.never()).save(any(User.class));
    }

    @Test
    void registerThrowsOnInvalidUsername() {
        assertThatThrownBy(() -> authService.register("raw-token", "ab", "password123"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void registerThrowsOnShortPassword() {
        assertThatThrownBy(() -> authService.register("raw-token", "alice", "shor"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void registerAcceptsUsernameWithCyrillicHyphenSpaceAndUnderscore() {
        InviteToken token = validToken();
        when(tokenService.validateToken("raw-token")).thenReturn(token);
        when(userRepository.existsByUsername("Иван-Петров Иванов_123")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encrypted");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });

        User result = authService.register("raw-token", "Иван-Петров Иванов_123", "password123");

        assertThat(result.getId()).isEqualTo(1L);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void registerThrowsOnForbiddenUsernameCharacter() {
        assertThatThrownBy(() -> authService.register("raw-token", "alice!", "password123"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    private InviteToken validToken() {
        InviteToken token = new InviteToken();
        token.setId(1L);
        token.setCodeHash("hash");
        token.setExpiresAt(LocalDateTime.now().plusDays(1));
        token.setUsed(false);
        return token;
    }
}