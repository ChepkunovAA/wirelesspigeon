package org.corpsman.forum.repository;

import org.corpsman.forum.model.ForumPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий постов форума.
 */
public interface ForumPostRepository extends JpaRepository<ForumPost, Long> {

    List<ForumPost> findByTopicIdOrderByCreatedAtAsc(Long topicId);

    Page<ForumPost> findByTopicIdAndParentPostIdIsNull(Long topicId, Pageable pageable);

    long countByTopicId(Long topicId);

    /**
     * Количество постов по тредам.
     *
     * @param topicIds идентификаторы тредов
     * @return массив [topicId, count]
     */
    @Query("SELECT p.topicId, COUNT(p) FROM ForumPost p WHERE p.topicId IN :topicIds GROUP BY p.topicId")
    List<Object[]> countByTopicIdIn(@Param("topicIds") List<Long> topicIds);

    @Modifying
    @Query("UPDATE ForumPost p SET p.likes = p.likes + 1 WHERE p.id = :id")
    int incrementLikes(@Param("id") Long id);

    @Modifying
    @Query("UPDATE ForumPost p SET p.likes = p.likes - 1 WHERE p.id = :id")
    int decrementLikes(@Param("id") Long id);

    @Modifying
    @Query("UPDATE ForumPost p SET p.dislikes = p.dislikes + 1 WHERE p.id = :id")
    int incrementDislikes(@Param("id") Long id);

    @Modifying
    @Query("UPDATE ForumPost p SET p.dislikes = p.dislikes - 1 WHERE p.id = :id")
    int decrementDislikes(@Param("id") Long id);

    Optional<ForumPost> findById(Long id);


    /**
     * Сумма лайков всех неудалённых комментариев автора.
     *
     * @param userId идентификатор автора
     * @return сумма лайков
     */
    Optional<ForumPost> findByTopicIdAndOpTrue(Long topicId);

    List<ForumPost> findByTopicIdAndParentPostIdIsNullAndContinuationTrueOrderByCreatedAtAsc(Long topicId);

    /**
     * Последний (самый новый) неудалённый пост по каждому из указанных тредов.
     * Только посты — стартовый (OP) или продолжение; комментарии не участвуют.
     *
     * @param topicIds идентификаторы тредов
     * @return список последних постов (по одному на тред)
     */
    @Query("SELECT p FROM ForumPost p WHERE p.deleted = false AND (p.op = true OR p.continuation = true) "
            + "AND p.topicId IN :topicIds "
            + "AND p.id = (SELECT MAX(p2.id) FROM ForumPost p2 WHERE p2.topicId = p.topicId "
            + "AND p2.deleted = false AND (p2.op = true OR p2.continuation = true))")
    List<ForumPost> findLatestPostEachByTopicIds(@Param("topicIds") List<Long> topicIds);


    /**
     * Последний пост (стартовый или продолжение) среди указанных тредов.
     *
     * @param topicIds  список доступных пользователю тредов
     * @param pageable первая страница (лимит 1)
     * @return список из одного поста
     */
    @Query("SELECT p FROM ForumPost p WHERE p.deleted = false AND (p.op = true OR p.continuation = true) "
            + "AND p.topicId IN :topicIds ORDER BY p.createdAt DESC, p.id DESC")
    List<ForumPost> findLatestByTopicIds(@Param("topicIds") List<Long> topicIds, Pageable pageable);

    /**
     * Лучший пост по рейтингу (лайки минус дизлайки) среди указанных тредов.
     *
     * @param topicIds  список доступных пользователю тредов
     * @param pageable первая страница (лимит 1)
     * @return список из одного поста
     */
    @Query("SELECT p FROM ForumPost p WHERE p.deleted = false AND (p.op = true OR p.continuation = true) "
            + "AND p.topicId IN :topicIds ORDER BY (p.likes - p.dislikes) DESC, p.id DESC")
    List<ForumPost> findBestByTopicIds(@Param("topicIds") List<Long> topicIds, Pageable pageable);

    @Query("SELECT p.id FROM ForumPost p WHERE p.authorId = :userId AND p.deleted = false")
    List<Long> findIdsByAuthor(@Param("userId") Long userId);

    @Query("SELECT COALESCE(SUM(p.likes), 0) FROM ForumPost p WHERE p.authorId = :userId AND p.deleted = false")
    long sumLikesByAuthor(@Param("userId") Long userId);

    /**
     * Сумма дизлайков всех неудалённых комментариев автора.
     *
     * @param userId идентификатор автора
     * @return сумма дизлайков
     */
    @Query("SELECT COALESCE(SUM(p.dislikes), 0) FROM ForumPost p WHERE p.authorId = :userId AND p.deleted = false")
    long sumDislikesByAuthor(@Param("userId") Long userId);
}
