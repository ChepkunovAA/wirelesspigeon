package org.corpsman.forum.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Краткое представление треда для списка.
 */
@Getter
@Setter
public class TopicSummaryDto {

    private Long id;
    private Long authorId;
    private String title;
    private String authorName;
    private boolean anonymous;
    private String firstPostPreview;
    private LocalDateTime createdAt;
    private LocalDateTime lastActivityAt;
    private long postsCount;
    private int likes;
    private int dislikes;
    private boolean frozen;
    private boolean deleted;
    private String visibility;
    private boolean readOnly;
    private boolean canInteract;
    private List<String> tags;
    private boolean author;
    private String realAuthorName;
}