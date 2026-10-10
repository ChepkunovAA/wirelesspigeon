package org.corpsman.rpg.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Элемент списка (для type=LIST): обязательный заголовок, опциональное описание
 * и уровень (используется для группировки заклинаний по уровню 1–9).
 */
@Data
@NoArgsConstructor
public class ListItem {

    private String header;

    private String description;

    private Integer level;

    public ListItem(String header, String description) {
        this(header, description, null);
    }

    public ListItem(String header, String description, Integer level) {
        this.header = header;
        this.description = description;
        this.level = level;
    }
}
