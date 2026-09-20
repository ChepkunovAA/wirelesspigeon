package org.corpsman.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Перечень всех бизнес-ошибок приложения с HTTP-статусом и сообщением по умолчанию.
 */
@Getter
public enum ErrorCode {

    INVALID_TOKEN(HttpStatus.BAD_REQUEST, "Неверный ключ регистрации"),
    TOKEN_EXPIRED(HttpStatus.BAD_REQUEST, "Токен истек"),
    TOKEN_ALREADY_USED(HttpStatus.BAD_REQUEST, "Ключ уже использован"),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "Некорректный ввод данных"),
    USERNAME_TAKEN(HttpStatus.CONFLICT, "Имя пользователя уже занято"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "Пользователь не найден"),
    CANNOT_BAN_SELF(HttpStatus.BAD_REQUEST, "Нельзя забанить себя"),
    CANNOT_BAN_ADMIN(HttpStatus.BAD_REQUEST, "Нельзя забанить мастера"),
    SUBSCRIPTION_SELF(HttpStatus.BAD_REQUEST, "Нельзя подписаться на себя"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Доступ запрещён"),
    BAD_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Неверный логин или пароль"),

    FILM_NOT_FOUND(HttpStatus.NOT_FOUND, "Фильм не найден"),
    FILM_ALREADY_EXISTS(HttpStatus.CONFLICT, "Вы уже предлагали этот фильм"),
    FILM_ALREADY_SELECTED(HttpStatus.CONFLICT, "Фильм уже выбран для просмотра"),
    NO_FILMS_AVAILABLE(HttpStatus.BAD_REQUEST, "Нет доступных фильмов для выбора"),
    FILM_NOT_SELECTED(HttpStatus.BAD_REQUEST, "Фильм не выбран для просмотра"),
    FILM_NOT_WATCHED(HttpStatus.BAD_REQUEST, "Фильм ещё не просмотрен"),
    FILM_CANNOT_DELETE(HttpStatus.BAD_REQUEST, "Фильм нельзя удалить в текущем статусе"),
    INVALID_RATING(HttpStatus.BAD_REQUEST, "Оценка должна быть от 1 до 4"),

    GIFT_NOT_FOUND(HttpStatus.NOT_FOUND, "Подарок не найден"),
    PERMISSION_DENIED(HttpStatus.FORBIDDEN, "У вас нет прав на это действие"),
    CANNOT_RESERVE_OWN_GIFT(HttpStatus.BAD_REQUEST, "Нельзя зарезервировать свой подарок"),
    ALREADY_RESERVED(HttpStatus.BAD_REQUEST, "Вы уже зарезервировали этот подарок"),
    LIMIT_REACHED(HttpStatus.BAD_REQUEST, "Лимит резервирования достигнут"),
    NO_RESERVATION(HttpStatus.BAD_REQUEST, "Резерв не найден"),
    LIMIT_TOO_LOW(HttpStatus.BAD_REQUEST, "Новый лимит меньше текущего количества резервов"),
    CONCURRENT_RESERVATION(HttpStatus.CONFLICT, "Подарок уже зарезервирован другим пользователем"),

    TOPIC_NOT_FOUND(HttpStatus.NOT_FOUND, "Тред не найден"),
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "Сообщение не найдено"),
    TAG_NOT_FOUND(HttpStatus.NOT_FOUND, "Тег не найден"),
    TOPIC_FROZEN(HttpStatus.BAD_REQUEST, "Тред заморожен. Новые комментарии запрещены"),
    TOPIC_DELETED(HttpStatus.BAD_REQUEST, "Тред удалён"),
    USER_BANNED(HttpStatus.FORBIDDEN, "Вы забанены в этом треде"),
    CANNOT_LIKE_OWN_POST(HttpStatus.BAD_REQUEST, "Нельзя лайкнуть свой пост"),
    CANNOT_DISLIKE_OWN_POST(HttpStatus.BAD_REQUEST, "Нельзя поставить дизлайк своему посту"),
    CANNOT_BAN_AUTHOR(HttpStatus.BAD_REQUEST, "Нельзя забанить автора треда"),
    ALREADY_BANNED(HttpStatus.BAD_REQUEST, "Пользователь уже забанен в этом треде"),
    USER_NOT_BANNED(HttpStatus.BAD_REQUEST, "Пользователь не забанен в этом треде"),
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "Файл не найден"),
    FILE_STORAGE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось сохранить файл"),

    BLOG_NOT_FOUND(HttpStatus.NOT_FOUND, "Блог не найден"),
    BLOG_PRIVATE(HttpStatus.FORBIDDEN, "Блог приватный. Доступ только для владельца"),
    BLOG_POST_NOT_FOUND(HttpStatus.NOT_FOUND, "Пост не найден"),
    BLOG_COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Комментарий не найден"),
    BLOG_COMMENTS_DISABLED(HttpStatus.BAD_REQUEST, "Комментарии отключены в этом блоге"),
    BLOG_USER_BANNED(HttpStatus.FORBIDDEN, "Вы забанены в этом блоге"),
    BLOG_ALREADY_BANNED(HttpStatus.BAD_REQUEST, "Пользователь уже забанен в этом блоге"),
    BLOG_USER_NOT_BANNED(HttpStatus.BAD_REQUEST, "Пользователь не забанен в этом блоге"),
    CANNOT_BAN_BLOG_OWNER(HttpStatus.BAD_REQUEST, "Нельзя забанить владельца блога"),
    CANNOT_VOTE_OWN_BLOG_POST(HttpStatus.BAD_REQUEST, "Нельзя оценивать свой пост"),
    CANNOT_VOTE_OWN_BLOG_COMMENT(HttpStatus.BAD_REQUEST, "Нельзя оценивать свой комментарий"),

    GAME_NOT_FOUND(HttpStatus.NOT_FOUND, "Игра не найдена"),
    GAME_PERMISSION_DENIED(HttpStatus.FORBIDDEN, "Нет доступа к игре"),
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "Комната не найдена"),
    ROOM_POST_NOT_FOUND(HttpStatus.NOT_FOUND, "Пост не найден"),
    ROOM_ACCESS_DENIED(HttpStatus.FORBIDDEN, "Нет доступа к комнате"),
    SHEET_NOT_FOUND(HttpStatus.NOT_FOUND, "Лист персонажа не найден"),
    SHEET_PERMISSION_DENIED(HttpStatus.FORBIDDEN, "Нет доступа к листу персонажа"),
    APPLICATION_ALREADY_EXISTS(HttpStatus.CONFLICT, "Вы уже подали заявку на эту игру"),
    APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Заявка не найдена"),
    CANNOT_APPLY_SELF(HttpStatus.BAD_REQUEST, "Нельзя подать заявку на свою игру"),
    INVALID_DICE(HttpStatus.BAD_REQUEST, "Некорректный бросок костей"),
    SAVED_ROLL_NOT_FOUND(HttpStatus.NOT_FOUND, "Сохранённый бросок не найден");

    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}