package org.corpsman.forum.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Представление поста форума.
 */
@Getter
@Setter
public class ForumPostDto {

private Long id;
    private Long parentPostId;
    private Long replyToId;
    private String parentContent;
    private String parentAuthorName;
    private long subtreeSize;
    private String content;
    private String authorName;
    private boolean anonymous;
    private boolean deleted;
    private boolean deletedByAdmin;
    private boolean deletedByOwner;
    private int likes;
    private boolean likedByCurrentUser;
    private int dislikes;
    private boolean dislikedByCurrentUser;
    private boolean author;
    private LocalDateTime createdAt;
    private boolean edited;
    private boolean op;
    private boolean continuation;
    private boolean latestContinuation;
    private Long authorId;
    private String realAuthorName;
    private String avatarPath;
    private List<ForumAttachmentDto> attachments = new ArrayList<>();
    private List<ForumPostDto> replies = new ArrayList<>();

    /**
     * Возвращает грамматически корректную форму слова "комментарий" для {@link #subtreeSize}.
     *
     * @return "комментарий", "комментария" или "комментариев"
     */
    public String getSubtreeWord() {
        long n = subtreeSize;
        long n10 = n % 10;
        long n100 = n % 100;
        if (n10 == 1 && n100 != 11) {
            return "комментарий";
        }
        if (n10 >= 2 && n10 <= 4 && (n100 < 12 || n100 > 14)) {
            return "комментария";
        }
        return "комментариев";
    }
}