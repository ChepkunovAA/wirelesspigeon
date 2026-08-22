package org.corpsman.forum.repository;

import org.corpsman.forum.model.PostDislike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий дизлайков постов.
 */
public interface PostDislikeRepository extends JpaRepository<PostDislike, Long> {

    boolean existsByPostIdAndUserId(Long postId, Long userId);

    Optional<PostDislike> findByPostIdAndUserId(Long postId, Long userId);

    List<PostDislike> findByPostIdInAndUserId(List<Long> postIds, Long userId);

    void deleteByPostIdAndUserId(Long postId, Long userId);

    /**
     * Количество реакций пользователя.
     *
     * @param userId идентификатор пользователя
     * @return количество записей
     */
    long countByUserId(Long userId);

    /**
     * Количество реакций пользователя на перечисленный контент.
     *
     * @param userId идентификатор пользователя
     * @param ids    идентификаторы контента
     * @return количество записей
     */
    long countByUserIdAndPostIdIn(Long userId, java.util.Collection<Long> ids);
}
