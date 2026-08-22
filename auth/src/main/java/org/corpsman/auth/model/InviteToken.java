package org.corpsman.auth.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.corpsman.common.model.BaseEntity;

import java.time.LocalDateTime;

/**
 * Одноразовый ключ регистрации.
 * <p>
 * Хранится BCrypt-хэш сырого кода, ключ действует 7 дней с момента создания.
 */
@Entity
@Table(name = "invite_tokens")
@Getter
@Setter
public class InviteToken extends BaseEntity {

    @Column(name = "code_hash", nullable = false, unique = true)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean used = false;

    @Column(name = "created_by")
    private Long createdBy;

    /**
     * Гостевой ключ: регистрирует пользователя с ролью GUEST.
     */
    @Column(name = "guest_key", nullable = false)
    private boolean guestKey;
}