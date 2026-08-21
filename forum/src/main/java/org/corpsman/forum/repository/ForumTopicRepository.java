package org.corpsman.forum.repository;

import org.corpsman.forum.model.ForumTopic;
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
     * @param search   подстрока названия (пустая строка для пропуска)
     * @param hasTags  признак фильтрации по тегам
     * @param tags     фильтр по тегам (актуален при hasTags=true)
     * @param pageable постраничность
     * @return страница тредов
     */
    @Query("SELECT DISTINCT t FROM ForumTopic t LEFT JOIN t.tags tag " +
            "WHERE t.deleted = false " +
            "AND (:search = '' OR LOWER(t.title) LIKE LOWER(CONCAT('%', CAST(:search AS text), '%'))) " +
            "AND (:hasTags = false OR tag.name IN :tags)")
    Page<ForumTopic> search(@Param("search") String search,
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
    Optional<ForumTopic> findFirstByPublicPostTrueAndDeletedFalseOrderByCreatedAtDesc();
}