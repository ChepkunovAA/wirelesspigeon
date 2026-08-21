package org.corpsman.forum.repository;

import org.corpsman.forum.model.ForumTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий тегов форума.
 */
public interface ForumTagRepository extends JpaRepository<ForumTag, Long> {

    Optional<ForumTag> findByName(String name);

    List<ForumTag> findByNameIn(List<String> names);

    List<ForumTag> findAllByOrderByNameAsc();

    /**
     * Популярные теги (по количеству тредов).
     *
     * @return массив [имя тега, количество тредов], отсортирован по убыванию
     */
    @Query("SELECT t.name, SIZE(t.topics) FROM ForumTag t ORDER BY SIZE(t.topics) DESC")
    List<Object[]> findPopularTags();
}