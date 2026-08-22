package org.corpsman.auth.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Ключ регистрации для отображения администратору.
 */
@Getter
@Setter
@NoArgsConstructor
public class InviteTokenDto {

    private Long id;

    private String codeHash;

    private LocalDateTime expiresAt;

    private boolean used;
    private boolean guestKey;

    private Long createdBy;

    private String createdByUsername;

    private LocalDateTime createdAt;
}
