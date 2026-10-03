package org.corpsman.rpg.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.corpsman.common.model.BaseEntity;
import org.corpsman.rpg.dto.SystemAttributeDto;

import java.util.List;

/**
 * Определение кастомной ролевой системы для библиотеки систем.
 * <p>
 * Содержит название, описание, видимость и типовую анкету — состав параметров
 * листа, который автор указывает при публикации. При создании игры на этой
 * системе анкета материализуется в параметры листа, и ведущему не нужно
 * заполнять их вручную.
 */
@Entity
@Table(name = "rpg_system_definitions")
@Getter
@Setter
public class GameSystemDefinition extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 1000000)
    private String description;

    /**
     * Автор системы (создатель).
     */
    @Column(name = "author_id", nullable = false)
    private Long authorId;

    /**
     * Публичная система (видна всем) или приватная (только автору).
     */
    @Column(name = "is_public", nullable = false)
    private boolean isPublic = true;


    /**
     * Типовая анкета персонажа: JSON-массив
     * {@code [{"name":"...","type":"LIST","position":0,"listKind":"COMPLEX"}]}.
     */
    @Column(name = "template_attributes", length = 1000000)
    private String templateAttributes;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final TypeReference<List<SystemAttributeDto>> TEMPLATE_TYPE = new TypeReference<>() {
    };

    /**
     * Анкета как список объектов. Нечитаемый JSON считается пустой анкетой.
     */
    public List<SystemAttributeDto> getTemplateAttributeList() {
        if (templateAttributes == null || templateAttributes.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(templateAttributes, TEMPLATE_TYPE);
        } catch (Exception e) {
            return List.of();
        }
    }

    public void setTemplateAttributeList(List<SystemAttributeDto> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            this.templateAttributes = "[]";
            return;
        }
        try {
            this.templateAttributes = MAPPER.writeValueAsString(attributes);
        } catch (Exception e) {
            this.templateAttributes = "[]";
        }
    }

}
