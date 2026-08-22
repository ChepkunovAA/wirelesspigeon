package org.corpsman.common.util;

import java.security.SecureRandom;

/**
 * Генератор временных паролей для сброса администратором.
 */
public final class PasswordGenerator {

    private static final String ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int LENGTH = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordGenerator() {
    }

    /**
     * Генерирует случайный пароль из латинских букв и цифр.
     *
     * @return пароль длиной 5 символов
     */
    public static String generate() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
