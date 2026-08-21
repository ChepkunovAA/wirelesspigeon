package org.corpsman.cinema.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.corpsman.cinema.dto.CinemaDashboardDto;
import org.corpsman.cinema.dto.FilmDto;
import org.corpsman.cinema.exception.FilmAlreadySelectedException;
import org.corpsman.cinema.exception.NoFilmsAvailableException;
import org.corpsman.cinema.mapper.FilmMapper;
import org.corpsman.cinema.model.CensorshipRating;
import org.corpsman.cinema.model.Film;
import org.corpsman.cinema.model.FilmStatus;
import org.corpsman.cinema.repository.FilmRepository;
import org.corpsman.common.event.FilmAddedEvent;
import org.corpsman.common.event.FilmSelectedEvent;
import org.corpsman.common.event.FilmWatchedEvent;
import org.corpsman.common.exception.BusinessException;
import org.corpsman.common.exception.ErrorCode;
import org.corpsman.common.util.LogUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Сервис локального кинотеатра: предложения, выбор фильма, статусы.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CinemaService {

    private static final int MIN_YEAR = 1900;

    private final FilmRepository filmRepository;
    private final FilmMapper filmMapper;
    private final RatingService ratingService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Возвращает данные главной страницы кинотеатра (кешируется).
     *
     * @return данные главной страницы
     */
    @Cacheable(value = "cinemaDashboard", key = "'dashboard'")
    public CinemaDashboardDto getDashboard() {
        Film nextFilm = filmRepository.findByStatus(FilmStatus.SELECTED).orElse(null);
        Film lastFilm = filmRepository.findFirstByStatusOrderByWatchedAtDesc(FilmStatus.WATCHED).orElse(null);

        CinemaDashboardDto dto = new CinemaDashboardDto();
        dto.setNextFilm(nextFilm == null ? null : filmMapper.toDto(nextFilm));
        dto.setLastFilm(lastFilm == null ? null : filmMapper.toDto(lastFilm));
        if (lastFilm != null) {
            dto.setLastFilmStats(ratingService.getFilmStats(lastFilm.getId()));
        }
        return dto;
    }

    /**
     * Добавляет фильм в список предложений пользователя.
     *
     * @param dto    данные фильма
     * @param userId идентификатор пользователя
     * @return DTO созданного фильма
     * @throws BusinessException если такой фильм уже предложен или год недопустим
     */
    @CacheEvict(value = "cinemaDashboard", allEntries = true)
    @Transactional
    public FilmDto addFilm(FilmDto dto, Long userId) {
        validateYear(dto.getYear());
        String title = dto.getTitle().trim();
        boolean duplicate = filmRepository.existsByUserIdAndTitleIgnoreCaseAndStatus(
                userId, title, FilmStatus.PROPOSED);
        if (duplicate) {
            throw new BusinessException(ErrorCode.FILM_ALREADY_EXISTS);
        }

        Film film = new Film();
        film.setTitle(title);
        film.setYear(dto.getYear());
        film.setTrailerUrl(dto.getTrailerUrl());
        film.setUserId(userId);
        film.setStatus(FilmStatus.PROPOSED);
        filmRepository.save(film);

        log.info("Добавлен фильм '{}' ({}) пользователем {}", film.getTitle(), film.getYear(), LogUtils.userContext(userId));
        eventPublisher.publishEvent(new FilmAddedEvent(film.getId(), film.getTitle(), userId, LocalDateTime.now()));
        return filmMapper.toDto(film);
    }

    /**
     * Удаляет фильм из предложений (владелец или админ).
     *
     * @param filmId идентификатор фильма
     * @param userId идентификатор пользователя
     * @param isAdmin признак администратора
     * @throws BusinessException если нет прав или фильм не в статусе PROPOSED
     */
    @CacheEvict(value = "cinemaDashboard", allEntries = true)
    @Transactional
    public void deleteFilm(Long filmId, Long userId, boolean isAdmin) {
        Film film = findFilmEntity(filmId);
        if (!film.getUserId().equals(userId) && !isAdmin) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (film.getStatus() != FilmStatus.PROPOSED) {
            throw new BusinessException(ErrorCode.FILM_CANNOT_DELETE);
        }
        filmRepository.delete(film);
        log.info("Удалён фильм '{}' пользователем {}", film.getTitle(), LogUtils.userContext(userId));
    }

    /**
     * Возвращает список предложенных фильмов пользователя.
     *
     * @param userId идентификатор пользователя
     * @return список фильмов
     */
    @Transactional(readOnly = true)
    public List<FilmDto> getUserFilms(Long userId) {
        return filmMapper.toDtoList(filmRepository.findByUserIdAndStatus(userId, FilmStatus.PROPOSED));
    }

    /**
     * Возвращает фильм по идентификатору.
     *
     * @param filmId идентификатор фильма
     * @return DTO фильма
     * @throws BusinessException если фильм не найден
     */
    @Transactional(readOnly = true)
    public FilmDto getFilm(Long filmId) {
        return filmMapper.toDto(findFilmEntity(filmId));
    }

    /**
     * Случайно выбирает следующий фильм для просмотра.
     * <p>
     * Сначала выбирается случайный пользователь, имеющий предложения, затем
     * случайный фильм из его списка. Выбранный фильм получает статус SELECTED.
     *
     * @param replace заменить уже выбранный фильм
     * @param discard сбросить уже выбранный фильм (перевести в DISCARDED) вместо возврата в список
     * @param actorId администратор, выполняющий выбор
     * @return DTO выбранного фильма
     * @throws FilmAlreadySelectedException если фильм уже выбран и replace = false
     * @throws NoFilmsAvailableException     если нет доступных фильмов
     */
    @CacheEvict(value = "cinemaDashboard", allEntries = true)
    @Transactional
    public FilmDto selectNextFilm(boolean replace, boolean discard, Long actorId) {
        Optional<Film> current = filmRepository.findByStatus(FilmStatus.SELECTED);
        Film currentFilm = null;
        if (current.isPresent() && !replace) {
            Film selected = current.get();
            throw new FilmAlreadySelectedException(selected.getId(), selected.getTitle());
        }
        if (current.isPresent()) {
            currentFilm = current.get();
            currentFilm.setStatus(discard ? FilmStatus.DISCARDED : FilmStatus.PROPOSED);
            filmRepository.save(currentFilm);
        }

        List<Long> userIds = filmRepository.findDistinctUserIdsByStatus(FilmStatus.PROPOSED);
        if (userIds.isEmpty()) {
            throw new NoFilmsAvailableException();
        }

        Long ownerId = userIds.get(ThreadLocalRandom.current().nextInt(userIds.size()));
        List<Film> films = filmRepository.findByUserIdAndStatus(ownerId, FilmStatus.PROPOSED);
        Long currentFilmId = currentFilm == null ? null : currentFilm.getId();
        if (currentFilm != null) {
            films = films.stream().filter(f -> !currentFilmId.equals(f.getId())).toList();
            if (films.isEmpty()) {
                throw new NoFilmsAvailableException();
            }
        }
        Film picked = films.get(ThreadLocalRandom.current().nextInt(films.size()));
        picked.setStatus(FilmStatus.SELECTED);
        filmRepository.save(picked);

        log.info("Выбран следующий фильм '{}' пользователя {}", picked.getTitle(), LogUtils.userContext(ownerId));
        eventPublisher.publishEvent(new FilmSelectedEvent(picked.getId(), picked.getTitle(), ownerId, actorId, LocalDateTime.now()));
        return filmMapper.toDto(picked);
    }

    /**
     * Переводит выбранный фильм в статус просмотренного.
     *
     * @param filmId  идентификатор фильма
     * @param actorId администратор, отметивший просмотр
     * @return DTO фильма
     * @throws BusinessException если фильм не в статусе SELECTED
     */
    @CacheEvict(value = "cinemaDashboard", allEntries = true)
    @Transactional
    public FilmDto markAsWatched(Long filmId, Long actorId) {
        Film film = findFilmEntity(filmId);
        if (film.getStatus() != FilmStatus.SELECTED) {
            throw new BusinessException(ErrorCode.FILM_NOT_SELECTED);
        }
        film.setStatus(FilmStatus.WATCHED);
        film.setWatchedAt(LocalDateTime.now());
        filmRepository.save(film);

        log.info("Фильм '{}' отмечен как просмотренный", film.getTitle());
        eventPublisher.publishEvent(new FilmWatchedEvent(film.getId(), actorId, LocalDateTime.now()));
        return filmMapper.toDto(film);
    }

    /**
     * Устанавливает цензурный рейтинг просмотренного фильма.
     *
     * @param filmId  идентификатор фильма
     * @param rating  цензурный рейтинг
     * @return DTO фильма
     * @throws BusinessException если фильм не в статусе WATCHED
     */
    @CacheEvict(value = "cinemaDashboard", allEntries = true)
    @Transactional
    public FilmDto setCensorship(Long filmId, CensorshipRating rating) {
        Film film = findFilmEntity(filmId);
        if (film.getStatus() != FilmStatus.WATCHED) {
            throw new BusinessException(ErrorCode.FILM_NOT_WATCHED);
        }
        film.setCensorship(rating);
        filmRepository.save(film);

        log.info("Цензурный рейтинг фильма '{}' установлен: {}", film.getTitle(), rating.getDisplayName());
        return filmMapper.toDto(film);
    }

    private Film findFilmEntity(Long id) {
        return filmRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILM_NOT_FOUND));
    }

    private void validateYear(Integer year) {
        if (year != null && (year < MIN_YEAR || year > LocalDate.now().getYear())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "Год должен быть от " + MIN_YEAR + " до " + LocalDate.now().getYear());
        }
    }
}