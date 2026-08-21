package org.corpsman.forum.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Запрос на создание треда.
 */
@Getter
@Setter
public class TopicCreateRequest {

    @NotBlank(message = "Заголовок обязателен")
    @Size(min = 1, max = 200, message = "Заголовок должен быть от 1 до 200 символов")
    private String title;

    @NotBlank(message = "Текст обязателен")
    private String content;

    private boolean anonymous;

    private boolean publicPost;

    private List<String> tags = new ArrayList<>();
}