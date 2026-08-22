package org.corpsman.users.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.corpsman.auth.model.UserRole;

import java.time.LocalDateTime;

/**
 * Участник клуба в списке пользователей.
 */
@Getter
@Setter
@NoArgsConstructor
public class MemberDto {

    private Long id;

    private String username;

    private UserRole role;

    private LocalDateTime lastLoginAt;
}
