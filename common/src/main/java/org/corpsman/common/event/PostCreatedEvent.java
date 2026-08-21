package org.corpsman.common.event;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Событие создания поста на форуме.
 */
@Getter
public class PostCreatedEvent extends BaseEvent {

    private final Long postId;
    private final Long forumId;
    private final boolean anonymous;

    /**
     * Создаёт событие создания поста.
     *
     * @param postId    идентификатор созданного поста
     * @param forumId   идентификатор темы форума
     * @param isAnonymous признак анонимного поста
     * @param userId    идентификатор автора (null для анонимных)
     * @param occurredAt момент создания
     */
    public PostCreatedEvent(Long postId, Long forumId, boolean isAnonymous, Long userId, LocalDateTime occurredAt) {
        super(userId, occurredAt);
        this.postId = postId;
        this.forumId = forumId;
        this.anonymous = isAnonymous;
    }
}