package org.corpsman.users.service;

import org.corpsman.auth.model.User;
import org.corpsman.auth.model.UserRole;
import org.corpsman.auth.repository.UserRepository;
import org.corpsman.common.event.UserDeletedEvent;
import org.corpsman.common.exception.BusinessException;
import org.corpsman.common.exception.ErrorCode;
import org.corpsman.users.dto.MemberDto;
import org.corpsman.users.mapper.MemberMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private MemberMapper memberMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private MemberService memberService;

    private User user(long id, String username, UserRole role, boolean active) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role);
        user.setActive(active);
        return user;
    }

    private MemberDto memberDto(long id, String username, UserRole role) {
        MemberDto dto = new MemberDto();
        dto.setId(id);
        dto.setUsername(username);
        dto.setRole(role);
        dto.setLastLoginAt(LocalDateTime.of(2026, 1, 1, 12, 0));
        return dto;
    }

    @Test
    void getMembersReturnsMappedActiveUsers() {
        User alice = user(1L, "alice", UserRole.RESIDENT, true);
        Pageable pageable = PageRequest.of(0, 20);
        when(userRepository.findByActiveTrue(pageable)).thenReturn(new PageImpl<>(List.of(alice)));
        when(memberMapper.toDto(alice)).thenReturn(memberDto(1L, "alice", UserRole.RESIDENT));

        var page = memberService.getMembers("", pageable);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getUsername()).isEqualTo("alice");
        assertThat(page.getContent().get(0).getLastLoginAt()).isNotNull();
    }

    @Test
    void getMembersSearchesByUsernameWhenSearchProvided() {
        User bob = user(2L, "bob", UserRole.RESIDENT, true);
        Pageable pageable = PageRequest.of(0, 20);
        when(userRepository.findByActiveTrueAndUsernameContainingIgnoreCase("bo", pageable))
                .thenReturn(new PageImpl<>(List.of(bob)));
        when(memberMapper.toDto(bob)).thenReturn(memberDto(2L, "bob", UserRole.RESIDENT));

        var page = memberService.getMembers(" bo ", pageable);

        verify(userRepository, never()).findByActiveTrue(any());
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getUsername()).isEqualTo("bob");
    }

    @Test
    void banUserDeactivatesUserAndPublishesEvent() {
        User target = user(5L, "victim", UserRole.RESIDENT, true);
        when(userRepository.findById(5L)).thenReturn(Optional.of(target));

        memberService.banUser(5L, 1L);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isFalse();

        ArgumentCaptor<UserDeletedEvent> eventCaptor = ArgumentCaptor.forClass(UserDeletedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getUserId()).isEqualTo(5L);
        assertThat(eventCaptor.getValue().getUsername()).isEqualTo("victim");
    }

    @Test
    void banUserFailsForSelf() {
        assertThatThrownBy(() -> memberService.banUser(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CANNOT_BAN_SELF);

        verify(userRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void banUserFailsForAdmin() {
        User admin = user(2L, "chief", UserRole.MASTER, true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> memberService.banUser(2L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CANNOT_BAN_ADMIN);

        verify(userRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void banUserFailsWhenNotFound() {
        when(userRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.banUser(9L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    void banUserFailsForAlreadyBannedUser() {
        User banned = user(3L, "ghost", UserRole.RESIDENT, false);
        when(userRepository.findById(3L)).thenReturn(Optional.of(banned));

        assertThatThrownBy(() -> memberService.banUser(3L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(userRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }
}
