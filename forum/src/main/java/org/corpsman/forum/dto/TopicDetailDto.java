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
    private boolean frozen;
    private boolean deleted;
    private boolean author;
    private boolean canModerate;
    private boolean admin;
    private boolean banned;
    private List<String> tags;
    private List<TopicBanDto> bannedUsers;
    private String realAuthorName;
}