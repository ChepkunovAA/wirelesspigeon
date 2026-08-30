package org.corpsman.forum.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Публичный пост, показываемый на главной странице.
 */
@Getter
@Setter
public class PublicPostDto {

    private Long id;

    private String title;

    private String authorName;

    private boolean anonymous;

    private String firstPostPreview;

    private LocalDateTime createdAt;

    private int likes;

    private int dislikes;

    private List<ForumAttachmentDto> attachments;
}