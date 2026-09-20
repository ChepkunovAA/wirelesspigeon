package org.corpsman.rpg.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.corpsman.common.model.BaseEntity;

/**
 * Член белого списка комнаты (доступ к приватной комнате).
 */
@Entity
@Table(name = "rpg_room_members",
        uniqueConstraints = @UniqueConstraint(name = "uk_rpg_room_member", columnNames = {"room_id", "user_id"}))
@Getter
@Setter
public class RoomMember extends BaseEntity {

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "user_id", nullable = false)
    private Long userId;
}