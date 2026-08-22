package org.corpsman.auth.repository;

import org.corpsman.auth.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий пользователей.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Находит пользователя по имени.
     *
     * @param username имя пользователя
     * @return пользователь, если найден
     */
    Optional<User> findByUsername(String username);

    /**
     * Находит пользователей по идентификаторам.
     *
     * @param ids идентификаторы пользователей
     * @return список пользователей
     */
    List<User> findByIdIn(Collection<Long> ids);

    /**
     * Проверяет существование пользователя с заданным именем.
     *
     * @param username имя пользователя
     * @return true, если пользователь существует
     */
    boolean existsByUsername(String username);

    /**
     * Находит всех пользователей по признаку активности.
     *
     * @param active признак активности
     * @return список пользователей
     */
    List<User> findByActive(boolean active);

    /**
     * Находит страницу активных пользователей.
     *
     * @param pageable пагинация и сортировка
     * @return страница активных пользователей
     */
    Page<User> findByActiveTrue(Pageable pageable);

    /**
     * Находит страницу активных пользователей с поиском по нику
     * (case-insensitive, частичное совпадение).
     *
     * @param username часть имени пользователя
     * @param pageable пагинация и сортировка
     * @return страница активных пользователей
     */
    Page<User> findByActiveTrueAndUsernameContainingIgnoreCase(String username, Pageable pageable);

    /**
     * Находит пользователей по идентификаторам с поиском по нику и пагинацией.
     *
     * @param ids      идентификаторы пользователей
     * @param search   поиск по нику (case-insensitive, частичное совпадение; может быть null)
     * @param pageable пагинация и сортировка
     * @return страница пользователей
     */
    @Query("SELECT u FROM User u WHERE u.id IN :ids "
            + "AND (:search IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))")
    Page<User> searchByIdsIn(@Param("ids") Collection<Long> ids,
                             @Param("search") String search,
                             Pageable pageable);
}