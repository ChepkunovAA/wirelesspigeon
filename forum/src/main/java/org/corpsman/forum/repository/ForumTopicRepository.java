package org.corpsman.forum.repository;

import org.corpsman.forum.model.ForumTopic;
import org.corpsman.forum.model.TopicVisibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий тредов форума.
 */
public interface ForumTopicRepository extends JpaRepository<ForumTopic, Long> {

    /**
     * Поиск тредов по названию и тегам с сортировкой по последней активности.
     *
     * @param search    подстрока названия (пустая строка для пропуска)
     * @param hidePublic признак скрытия публичных тредов (кроме собственных)
     *                  — пользователь с showPublicPosts=false видит свои публичные треды
     * @param typeFilters фильтр по типам видимости (null — без фильтра)
     * @param hasTags   признак фильтрации по тегам
     * @param tags      фильтр по тегам (актуален при hasTags=true)
     * @param pageable  постраничность
     * @return страница тредов
     */
    @Query("SELECT DISTINCT t FROM ForumTopic t LEFT JOIN t.tags tag " +
            "WHERE t.deleted = false " +
            "AND (:hidePublic = false OR t.visibility <> 'PUBLIC' OR (:uid IS NOT NULL AND t.authorId = :uid)) " +
            "AND (:guest = false OR t.visibility <> 'OPEN') "
            + "AND (t.visibility <> 'CLOSED' OR (:uid IS NOT NULL AND t.authorId = :uid) "
            + "OR EXISTS (SELECT w FROM TopicWhitelistEntry w WHERE w.topicId = t.id AND w.userId = :uid)) " +
            "AND (:typeFilters IS NULL OR t.visibility IN :typeFilters) " +
            "AND (:guestHide = false OR t.visibility <> 'GUEST') " +
            "AND (:search = '' OR LOWER(t.title) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%'))) " +
            "AND (:hasTags = false OR tag.name IN :tags) " +
            "ORDER BY t.lastActivityAt DESC")
    Page<ForumTopic> search(@Param("search") String search,
                            @Param("hidePublic") boolean hidePublic,
                            @Param("guestHide") boolean guestHide,
                            @Param("typeFilters") List<TopicVisibility> typeFilters,
                            @Param("guest") boolean guest,
                            @Param("uid") Long uid,
                            @Param("hasTags") boolean hasTags,
                            @Param("tags") List<String> tags,
                            Pageable pageable);

    @Modifying
    @Query("UPDATE ForumTopic t SET t.likes = t.likes + 1 WHERE t.id = :id")
    int incrementLikes(@Param("id") Long id);

    @Modifying
    @Query("UPDATE ForumTopic t SET t.likes = t.likes - 1 WHERE t.id = :id")
    int decrementLikes(@Param("id") Long id);

    @Modifying
    @Query("UPDATE ForumTopic t SET t.dislikes = t.dislikes + 1 WHERE t.id = :id")
    int incrementDislikes(@Param("id") Long id);

    @Modifying
    @Query("UPDATE ForumTopic t SET t.dislikes = t.dislikes - 1 WHERE t.id = :id")
    int decrementDislikes(@Param("id") Long id);

    /**
     * Возвращает последний опубликованный публичный тред.
     *
     * @return последний публичный тред (не удалённый)
     */
    Optional<ForumTopic> findFirstByVisibilityAndDeletedFalseOrderByCreatedAtDesc(
            @Param("visibility") TopicVisibility visibility);

    /**
     * Все неудалённые треды форума.
     *
     * @return список тредов
     */
    @Query("SELECT t FROM ForumTopic t WHERE t.deleted = false")
    List<ForumTopic> findAllByDeletedFalse();


    /**
     * Сумма лайков всех неудалённых тредов автора.
     *
     * @param userId идентификатор автора
     * @return сумма лайков
     */
    @Query("SELECT t.id FROM ForumTopic t WHERE t.authorId = :userId AND t.deleted = false")
    List<Long> findIdsByAuthor(@Param("userId") Long userId);

    @Query("SELECT COALESCE(SUM(t.likes), 0) FROM ForumTopic t WHERE t.authorId = :userId AND t.deleted = false")
    long sumLikesByAuthor(@Param("userId") Long userId);

    /**
     * Сумма дизлайков всех неудалённых тредов автора.
     *
     * @param userId идентификатор автора
     * @return сумма дизлайков
     */
    @Query("SELECT COALESCE(SUM(t.dislikes), 0) FROM ForumTopic t WHERE t.authorId = :userId AND t.deleted = false")
    long sumDislikesByAuthor(@Param("userId") Long userId);
}
