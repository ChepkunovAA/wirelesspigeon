package org.corpsman.forum.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Полное представление треда.
 */
@Getter
@Setter
public class TopicDetailDto {

    private Long id;
    private Long topicAuthorId;
    private String title;
    private ForumPostDto firstPost;
    private Page<ForumPostDto> posts;
    /** Единая хронологическая лента первого уровня (для плоского режима). */
    private List<ForumPostDto> timeline = new java.util.ArrayList<>();
    private boolean frozen;
    private boolean deleted;
    private boolean author;
    private boolean canModerate;
    private boolean admin;
    private boolean banned;
    private List<String> tags;
    private String visibility;
    private boolean readOnly;
    private boolean canWrite;
    private List<TopicBanDto> bannedUsers;
    private String realAuthorName;
}