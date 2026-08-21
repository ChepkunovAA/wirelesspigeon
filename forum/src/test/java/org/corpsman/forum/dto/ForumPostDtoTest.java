package org.corpsman.forum.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ForumPostDtoTest {

    private final ForumPostDto dto = new ForumPostDto();

    @Test
    void subtreeWordSingular() {
        dto.setSubtreeSize(1);
        assertThat(dto.getSubtreeWord()).isEqualTo("комментарий");
        dto.setSubtreeSize(21);
        assertThat(dto.getSubtreeWord()).isEqualTo("комментарий");
        dto.setSubtreeSize(101);
        assertThat(dto.getSubtreeWord()).isEqualTo("комментарий");
    }

    @Test
    void subtreeWordFew() {
        dto.setSubtreeSize(2);
        assertThat(dto.getSubtreeWord()).isEqualTo("комментария");
        dto.setSubtreeSize(4);
        assertThat(dto.getSubtreeWord()).isEqualTo("комментария");
        dto.setSubtreeSize(23);
        assertThat(dto.getSubtreeWord()).isEqualTo("комментария");
        dto.setSubtreeSize(114);
        assertThat(dto.getSubtreeWord()).isEqualTo("комментариев");
    }

    @Test
    void subtreeWordMany() {
        dto.setSubtreeSize(5);
        assertThat(dto.getSubtreeWord()).isEqualTo("комментариев");
        dto.setSubtreeSize(11);
        assertThat(dto.getSubtreeWord()).isEqualTo("комментариев");
        dto.setSubtreeSize(20);
        assertThat(dto.getSubtreeWord()).isEqualTo("комментариев");
    }
}