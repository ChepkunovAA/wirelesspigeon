package org.corpsman.rpg.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Форма публикации кастомной ролевой системы в общую библиотеку.
 * <p>
 * Автор указывает название, видимость и состав параметров листа — типовую
 * анкету. Для параметра типа {@code LIST} задаётся вид списка: «Простой»
 * (элемент — строка) или «Сложный» (заголовок, описание, уровень).
 * При создании игры на этой системе анкета применяется к листам, поэтому
 * ведущему не нужно заполнять параметры вручную.
 */
@Data
public class GameSystemDefinitionForm {

    @NotBlank(message = "Название системы обязательно")
    @Size(max = 200, message = "Название не должно превышать 200 символов")
    private String name;

    @Size(max = 1000000, message = "Описание слишком длинное")
    private String description;

    /**
     * Публичная ли система (видна всем) или приватная (только автору).
     * Имя {@code published}, а не {@code isPublic}: Thymeleaf не биндит
     * примитивное булево поле с префиксом {@code is}.
     */
    private boolean published = true;

    /**
     * Состав параметров листа (типовая анкета персонажа).
     */
    private List<SystemAttributeDto> templateAttributes = new ArrayList<>();
}
