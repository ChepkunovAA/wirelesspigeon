package org.corpsman.common.util;

import org.corpsman.common.exception.BusinessException;
import org.corpsman.common.exception.ErrorCode;

import java.time.LocalDate;
import java.util.regex.Pattern;

/**
 * Утилиты валидации входных данных.
 */
public final class ValidationUtils {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-zА-Яа-яЁё0-9_\\- ]{3,30}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final int MIN_PASSWORD_LENGTH = 5;

    private ValidationUtils() {
    }

    /**
     * Проверяет, что строка не является пустой.
     *
     * @param value значение
     * @param field имя поля для сообщения об ошибке
     * @throws BusinessException если значение пустое
     */
    public static void requireNotBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, field + " не может быть пустым");
        }
    }

    /**
     * Проверяет имя пользователя на допустимый формат.
     *
     * @param username имя пользователя
     * @throws BusinessException если формат недопустим
     */
    public static void validateUsername(String username) {
        requireNotBlank(username, "Имя пользователя");
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "Имя пользователя должно содержать 3-30 символов: кириллица, латиница, цифры, дефис, подчёркивание, пробел");
        }
    }

    /**
     * Проверяет пароль на минимальную длину.
     *
     * @param password пароль
     * @throws BusinessException если пароль слишком короткий
     */
    public static void validatePassword(String password) {
        requireNotBlank(password, "Пароль");
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "Пароль должен содержать не менее " + MIN_PASSWORD_LENGTH + " символов");
        }
    }

    /**
     * Проверяет корректность email.
     *
     * @param email адрес электронной почты
     * @throws BusinessException если email некорректен
     */
    public static void validateEmail(String email) {
        requireNotBlank(email, "Email");
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Некорректный адрес электронной почты");
        }
    }

    /**
     * Проверяет, что дата не в будущем.
     *
     * @param date  дата
     * @param field имя поля для сообщения об ошибке
     * @throws BusinessException если дата в будущем
     */
    public static void validateNotInFuture(LocalDate date, String field) {
        if (date != null && date.isAfter(LocalDate.now())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, field + " не может быть в будущем");
        }
    }

    /**
     * Проверяет условие и выбрасывает исключение при его ложности.
     *
     * @param condition проверяемое условие
     * @param errorCode код ошибки
     * @param message   сообщение об ошибке
     * @throws BusinessException если условие ложно
     */
    public static void isTrue(boolean condition, ErrorCode errorCode, String message) {
        if (!condition) {
            throw new BusinessException(errorCode, message);
        }
    }
}