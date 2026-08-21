package org.corpsman.auth.model;

/**
 * Роли пользователей закрытого клуба.
 */
public enum UserRole {

    /**
     * Обычный участник.
     */
    USER,

    /**
     * Администратор с доступом к /admin/**.
     */
    ADMIN
}