package org.corpsman.wishlist.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.corpsman.common.model.BaseEntity;

/**
 * Подарок в вишлисте пользователя.
 */
@Entity
@Table(name = "gifts", indexes = {
        @Index(name = "idx_gifts_owner_id", columnList = "owner_id")
})
@Getter
@Setter
public class Gift extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "reservation_limit", nullable = false)
    private Integer limit = 1;

    @Column(name = "reserved_count", nullable = false)
    private Integer reservedCount = 0;
}