package org.corpsman.rpg.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.corpsman.common.model.BaseEntity;

/**
 * Пост в комнате. Посты ведущего выделяются цветом (вычисляется по {@code masterId} игры).
 */
@Entity
@Table(name = "rpg_room_posts")
@Getter
@Setter
public class RoomPost extends BaseEntity {

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    /**
     * Лист, от имени которого опубликован пост (мастер пишет как NPC).
     */
    @Column(name = "sheet_id")
    private Long sheetId;

    @Column(nullable = false, length = 1000000)
    private String content;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;
}