package org.corpsman.wishlist.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Данные для создания или редактирования подарка.
 */
@Getter
@Setter
public class GiftCreateDto {

    private Long id;

    @NotBlank(message = "Название обязательно")
    @Size(min = 2, max = 200, message = "Название должно быть от 2 до 200 символов")
    private String title;

    @Size(max = 1000, message = "Описание слишком длинное")
    private String description;

    @NotNull(message = "Лимит обязателен")
    @Min(value = 0, message = "Лимит не может быть отрицательным")
    private Integer limit = 1;
}