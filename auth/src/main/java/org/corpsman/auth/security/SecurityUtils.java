package org.corpsman.auth.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Утилиты доступа к текущему аутентифицированному пользователю.
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * Возвращает детали текущего пользователя.
     *
     * @return детали пользователя или null, если пользователь не аутентифицирован
     */
    public static CorpsmanUserDetails currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CorpsmanUserDetails userDetails) {
            return userDetails;
        }
        return null;
    }

    /**
     * Возвращает идентификатор текущего пользователя.
     *
     * @return идентификатор пользователя или null, если пользователь не аутентифицирован
     */
    public static Long currentUserId() {
        CorpsmanUserDetails user = currentUser();
        return user == null ? null : user.getId();
    }
}