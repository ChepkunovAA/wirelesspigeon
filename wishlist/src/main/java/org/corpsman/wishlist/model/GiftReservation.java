package org.corpsman.wishlist.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.corpsman.common.model.BaseEntity;

import java.time.LocalDateTime;

/**
 * Резервирование подарка пользователем.
 * <p>
 * Один пользователь может зарезервировать подарок только один раз
 * (уникальность по паре {@code gift_id, user_id}).
 */
@Entity
@Table(name = "gift_reservations",
        uniqueConstraints = @UniqueConstraint(name = "uk_gift_reservation_unique", columnNames = {"gift_id", "user_id"}),
        indexes = {
                @Index(name = "idx_reservations_gift_id", columnList = "gift_id"),
                @Index(name = "idx_reservations_user_id", columnList = "user_id")
        })
@Getter
@Setter
public class GiftReservation extends BaseEntity {

    @Column(name = "gift_id", nullable = false)
    private Long giftId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "reserved_at", nullable = false)
    private LocalDateTime reservedAt;
}