package org.corpsman.rpg.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
/**
 * DTO для отображения определения ролевой системы в библиотеке.
 */
@Data
public class GameSystemDefinitionDto {

    private Long id;
    private String name;
    private String description;
    private Long authorId;
    private boolean isPublic;
    private LocalDateTime createdAt;

    /**
     * Состав параметров листа, заданный автором при публикации.
     */
    private List<SystemAttributeDto> templateAttributes = new ArrayList<>();
}
