package org.corpsman.cinema.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.corpsman.auth.model.UserRole;

import java.time.LocalDateTime;

/**
 * Публичный профиль пользователя кинотеатра.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PublicProfileDto {

    private Long id;

    private String username;

    private UserRole role;

    private LocalDateTime createdAt;

    private Double averageRating;

    private Double corruption;

    private Double averageViewerToOwner;

    private Double averageOwnerToViewer;
}