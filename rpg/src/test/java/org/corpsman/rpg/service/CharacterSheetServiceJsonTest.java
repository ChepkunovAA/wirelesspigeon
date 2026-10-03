package org.corpsman.rpg.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.corpsman.rpg.model.ListItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Формат хранения элементов списка в JSON: заголовок, описание, уровень.
 */
class CharacterSheetServiceJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private List<ListItem> roundTrip(String json) throws Exception {
        return objectMapper.readValue(json, new TypeReference<List<ListItem>>() {
        });
    }

    @Test
    void parsesObjectsWithHeaderDescriptionAndLevel() throws Exception {
        String json = "[{\"header\":\"Огненный шар\",\"description\":\"Урон 8d6\",\"level\":3},"
                + "{\"header\":\"Свет\",\"description\":\"Ослепление\",\"level\":1}]";

        List<ListItem> items = roundTrip(json);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).getHeader()).isEqualTo("Огненный шар");
        assertThat(items.get(0).getDescription()).isEqualTo("Урон 8d6");
        assertThat(items.get(0).getLevel()).isEqualTo(3);
        assertThat(items.get(1).getHeader()).isEqualTo("Свет");
        assertThat(items.get(1).getLevel()).isEqualTo(1);
    }

    @Test
    void parsesObjectsWithoutLevelAsNull() throws Exception {
        List<ListItem> items = roundTrip("[{\"header\":\"Щит\",\"description\":\"\",\"level\":null}]");

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getLevel()).isNull();
    }

    @Test
    void handlesEmptyArray() throws Exception {
        assertThat(roundTrip("[]")).isEmpty();
    }

    @Test
    void serializesAllThreeFields() throws Exception {
        String json = objectMapper.writeValueAsString(List.of(new ListItem("Щит", "AC +2", 2)));

        assertThat(json).contains("\"header\":\"Щит\"")
                .contains("\"description\":\"AC +2\"")
                .contains("\"level\":2");
    }

    @Test
    void levelMayBeAbsentForNonSpellLists() throws Exception {
        String json = objectMapper.writeValueAsString(List.of(new ListItem("Факел", "свет", null)));

        assertThat(roundTrip(json).get(0).getLevel()).isNull();
    }
}
