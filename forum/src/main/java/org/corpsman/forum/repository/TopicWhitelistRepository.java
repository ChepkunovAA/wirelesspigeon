package org.corpsman.forum.repository;

import org.corpsman.forum.model.TopicWhitelistEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * Репозиторий белого списка закрытых тредов.
 */
public interface TopicWhitelistRepository extends JpaRepository<TopicWhitelistEntry, Long> {

    /**
     * Все записи белого списка треда.
     *
     * @param topicId идентификатор треда
     * @return список записей
     */
    List<TopicWhitelistEntry> findByTopicId(Long topicId);

    /**
     * Все записи белого списка для указанных тредов.
     *
     * @param topicIds список тредов
     * @return список записей
     */
    List<TopicWhitelistEntry> findByTopicIdIn(Collection<Long> topicIds);

    /**
     * Существует ли запись для пользователя в треде.
     *
     * @param topicId идентификатор треда
     * @param userId  идентификатор пользователя
     * @return true, если пользователь в белом списке
     */
    boolean existsByTopicIdAndUserId(Long topicId, Long userId);

    /**
     * Удаляет все записи треда (массовый запрос, выполняется сразу).
     *
     * @param topicId идентификатор треда
     */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM TopicWhitelistEntry w WHERE w.topicId = :topicId")
    void deleteByTopicId(@org.springframework.data.repository.query.Param("topicId") Long topicId);
}
