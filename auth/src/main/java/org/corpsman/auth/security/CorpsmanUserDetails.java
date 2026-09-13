package org.corpsman.auth.security;

import lombok.Getter;
import org.corpsman.auth.model.User;
import org.corpsman.auth.model.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Пользователь Spring Security, оборачивающий доменную сущность {@link User}.
 */
@Getter
public class CorpsmanUserDetails implements UserDetails {

    private final Long id;
    private final String username;
    private final String passwordHash;
    private final UserRole role;
    private final boolean active;
    private final String avatarPath;

    public CorpsmanUserDetails(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.passwordHash = user.getPasswordHash();
        this.role = user.getRole();
        this.active = user.isActive();
        this.avatarPath = user.getAvatarPath();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}