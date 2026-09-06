package org.corpsman.auth.service;

import org.corpsman.auth.model.User;
import org.corpsman.auth.repository.UserRepository;
import org.corpsman.auth.repository.UserSettingsRepository;
import org.corpsman.common.exception.BusinessException;
import org.corpsman.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettingsServiceTest {

    @Mock
    private UserSettingsRepository settingsRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private SettingsService settingsService;

    private org.corpsman.auth.model.UserSettings settings(long userId, boolean threadAnon,
                                                          boolean commentAnon, String defaultVisibility,
                                                          boolean showPublic) {
        var s = new org.corpsman.auth.model.UserSettings();
        s.setUserId(userId);
        s.setDefaultThreadAnonymous(threadAnon);
        s.setDefaultCommentAnonymous(commentAnon);
        s.setDefaultVisibility(defaultVisibility);
        s.setShowPublicPosts(showPublic);
        return s;
    }

    @Test
    void getByUserIdCreatesDefaultsWhenMissing() {
        when(settingsRepository.findById(1L)).thenReturn(Optional.empty());
        when(settingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = settingsService.getByUserId(1L);

        ArgumentCaptor<org.corpsman.auth.model.UserSettings> captor =
                ArgumentCaptor.forClass(org.corpsman.auth.model.UserSettings.class);
        verify(settingsRepository).save(captor.capture());
        assertThat(result).isSameAs(captor.getValue());
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
        assertThat(captor.getValue().isDefaultThreadAnonymous()).isFalse();
        assertThat(captor.getValue().isDefaultCommentAnonymous()).isFalse();
        assertThat(captor.getValue().getDefaultVisibility()).isEqualTo("OPEN");
        assertThat(captor.getValue().isShowPublicPosts()).isTrue();
    }

    @Test
    void updatePersistsAllFlags() {
        var stored = settings(1L, false, false, "OPEN", true);
        when(settingsRepository.findById(1L)).thenReturn(Optional.of(stored));
        when(settingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        settingsService.update(1L, true, true, "CLOSED", false, false, false);

        ArgumentCaptor<org.corpsman.auth.model.UserSettings> captor =
                ArgumentCaptor.forClass(org.corpsman.auth.model.UserSettings.class);
        verify(settingsRepository).save(captor.capture());
        assertThat(captor.getValue().isDefaultThreadAnonymous()).isTrue();
        assertThat(captor.getValue().isDefaultCommentAnonymous()).isTrue();
        assertThat(captor.getValue().getDefaultVisibility()).isEqualTo("CLOSED");
        assertThat(captor.getValue().isDefaultReadOnly()).isFalse();
        assertThat(captor.getValue().isHideGuestThreads()).isFalse();
        assertThat(captor.getValue().isShowPublicPosts()).isFalse();
    }

    @Test
    void updatePersistsGuestDefaultVisibility() {
        var stored = settings(1L, false, false, "OPEN", true);
        when(settingsRepository.findById(1L)).thenReturn(Optional.of(stored));
        when(settingsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        settingsService.update(1L, false, false, "GUEST", false, true, false);

        ArgumentCaptor<org.corpsman.auth.model.UserSettings> captor =
                ArgumentCaptor.forClass(org.corpsman.auth.model.UserSettings.class);
        verify(settingsRepository).save(captor.capture());
        assertThat(captor.getValue().getDefaultVisibility()).isEqualTo("GUEST");
    }

    @Test
    void changePasswordVerifiesOldAndEncodesNew() {
        User user = new User();
        user.setId(1L);
        user.setPasswordHash("current-hash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("oldpass", "current-hash")).thenReturn(true);
        when(passwordEncoder.encode("newpass1")).thenReturn("encoded");

        settingsService.changePassword(1L, "oldpass", "newpass1");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("encoded");
    }

    @Test
    void changePasswordRejectsWrongOldPassword() {
        User user = new User();
        user.setId(1L);
        user.setPasswordHash("current-hash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "current-hash")).thenReturn(false);

        assertThatThrownBy(() -> settingsService.changePassword(1L, "wrong", "newpass1"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_CREDENTIALS);

        verify(userRepository, never()).save(any());
    }

    @Test
    void changePasswordValidatesNewPasswordLength() {
        assertThatThrownBy(() -> settingsService.changePassword(1L, "oldpass", "abc"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPasswordValidatesAndEncodesWithoutOldCheck() {
        User user = new User();
        user.setId(9L);
        user.setPasswordHash("old-hash");
        when(userRepository.findById(9L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("freshpass")).thenReturn("encoded");

        settingsService.resetPassword(9L, "freshpass");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("encoded");
    }

    @Test
    void resetPasswordRejectsTooShortPassword() {
        assertThatThrownBy(() -> settingsService.resetPassword(9L, "abc"))
                .isInstanceOf(BusinessException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void generateAndResetReturnsFiveCharAlnumAndEncodes() {
        User user = new User();
        user.setId(9L);
        when(userRepository.findById(9L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(any())).thenReturn("encoded");

        String raw = settingsService.generateAndReset(9L);

        assertThat(raw).matches("[A-Za-z0-9]{5}");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("encoded");
    }
}
