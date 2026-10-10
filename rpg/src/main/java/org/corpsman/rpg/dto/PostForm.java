package org.corpsman.rpg.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Форма создания поста в комнате с одним или несколькими бросками костей.
 */
@Data
public class PostForm {

    @NotBlank
    @Size(max = 1000000)
    private String content;

    private List<DieRollForm> rolls = new ArrayList<>();
}