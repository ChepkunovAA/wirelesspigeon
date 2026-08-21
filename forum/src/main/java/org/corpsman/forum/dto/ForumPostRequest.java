package org.corpsman.forum.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Запрос на создание комментария.
 */
@Getter
@Setter
public class ForumPostRequest {

    @NotNull(message = "Не указан тред")
    private Long topicId;

    private Long parentPostId;

    private Long replyToId;

    @NotBlank(message = "Текст комментария обязателен")
    @Size(min = 2, max = 5000, message = "Текст должен быть от 2 до 5000 символов")
    private String content;

    private boolean anonymous;
}