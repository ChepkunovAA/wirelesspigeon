package org.corpsman.rpg.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Форма листа персонажа (создание/редактирование).
 * <p>
 * Один лист «суммирует» все параметры в одном объекте: базовые поля отдельно,
 * параметры систем — списком {@link SheetAttributeForm}.
 */
@Data
public class SheetForm {

    @NotBlank
    @Size(max = 200)
    private String name;

    @Size(max = 1000000)
    private String kventa;

    private List<SheetAttributeForm> attributes = new ArrayList<>();
}