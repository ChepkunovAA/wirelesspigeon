package org.corpsman.common.event;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Событие создания треда на форуме.
 */
@Getter
public class TopicCreatedEvent extends BaseEvent {

    private final Long topicId;
    private final boolean anonymous;

    /**
     * Создаёт событие создания треда.
     *
     * @param topicId    идентификатор треда
     * @param isAnonymous признак анонимного треда
     * @param userId     идентификатор автора (null для анонимных)
     * @param occurredAt момент создания
     */
    public TopicCreatedEvent(Long topicId, boolean isAnonymous, Long userId, LocalDateTime occurredAt) {
        super(userId, occurredAt);
        this.topicId = topicId;
        this.anonymous = isAnonymous;
    }
}