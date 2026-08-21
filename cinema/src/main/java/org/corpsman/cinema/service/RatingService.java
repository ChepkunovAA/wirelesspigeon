package org.corpsman.cinema.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.corpsman.auth.model.User;
import org.corpsman.auth.repository.UserRepository;
import org.corpsman.cinema.dto.FilmDto;
import org.corpsman.cinema.dto.ProposerStatDto;
import org.corpsman.cinema.dto.PublicProfileDto;
import org.corpsman.cinema.dto.RatingStatsDto;
import org.corpsman.cinema.mapper.FilmMapper;
import org.corpsman.cinema.model.CensorshipRating;
import org.corpsman.cinema.model.Film;
import org.corpsman.cinema.model.FilmRating;
import org.corpsman.cinema.model.FilmStatus;
import org.corpsman.cinema.repository.FilmRatingRepository;
import org.corpsman.cinema.repository.FilmRepository;
import org.corpsman.common.exception.BusinessException;
import org.corpsman.common.exception.ErrorCode;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Сервис оценок фильмов.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RatingService {

    private static final int MIN_RATING = 0;
    private static final int MAX_RATING = 3;

    private static final int RATING_SCALE_STEPS = 3;
    private static final int DISPLAY_SCALE = 10;

    private final FilmRatingRepository ratingRepository;
    private final FilmRepository filmRepository;
    private final FilmMapper filmMapper;
    private final UserRepository userRepository;

    /**
     * Ставит или обновляет оценку фильма (UPSERT).
     *
     * @param filmId идентификатор фильма
     * @param userId идентификатор пользователя
     * @param rating оценка от 0 до 3
     * @param review опциональный отзыв
     * @throws BusinessException если фильм не просмотрен или оценка вне диапазона
     */
    @CacheEvict(cacheNames = {"filmStats", "cinemaDashboard"}, allEntries = true)
    public void rateFilm(Long filmId, Long userId, Integer rating, String review) {
        requireWatched(filmId);
        validateRating(rating);
        FilmRating existing = ratingRepository.findByFilmIdAndUserId(filmId, userId).orElse(null);
        if (existing == null) {
            FilmRating newRating = new FilmRating();
            newRating.setFilmId(filmId);
            newRating.setUserId(userId);
            newRating.setRating(rating);
            newRating.setReview(review);
            ratingRepository.save(newRating);
        } else {
            existing.setRating(rating);
            existing.setReview(review);
            ratingRepository.save(existing);
        }
        log.info("Оценка фильма {} = {} пользователем {}", filmId, rating, userId);
    }

    /**
     * Отзывает оценку пользователя.
     *
     * @param filmId идентификатор фильма
     * @param userId идентификатор пользователя
     */
    @Transactional
    @CacheEvict(cacheNames = {"filmStats", "cinemaDashboard"}, allEntries = true)
    public void deleteRating(Long filmId, Long userId) {
        ratingRepository.deleteByFilmIdAndUserId(filmId, userId);
        log.info("Оценка фильма {} отозвана пользователем {}", filmId, userId);
    }

    /**
     * Возвращает оценку пользователя по фильму.
     *
     * @param filmId идентификатор фильма
     * @param userId идентификатор пользователя
     * @return оценка или null
     */
    @Transactional(readOnly = true)
    public Integer getUserRating(Long filmId, Long userId) {
        if (userId == null) {
            return null;
        }
        return ratingRepository.findByFilmIdAndUserId(filmId, userId)
                .map(FilmRating::getRating)
                .orElse(null);
    }

    /**
     * Возвращает статистику оценок фильма (кешируется).
     *
     * @param filmId идентификатор фильма
     * @return статистика
     */
    @Cacheable(value = "filmStats", key = "#filmId")
    public RatingStatsDto getFilmStats(Long filmId) {
        List<FilmRating> ratings = ratingRepository.findByFilmId(filmId);
        if (ratings.isEmpty()) {
            return new RatingStatsDto(0.0, 0, new LinkedHashMap<>());
        }
        double average = normalizeToDisplay(ratings.stream().mapToInt(FilmRating::getRating).average().orElse(0.0));
        Map<Integer, Long> distribution = ratings.stream()
                .collect(Collectors.groupingBy(FilmRating::getRating, LinkedHashMap::new, Collectors.counting()));
        log.debug("Статистика фильма {}: средняя={}, голосов={}", filmId, average, ratings.size());
        return new RatingStatsDto(average, ratings.size(), distribution);
    }

    /**
     * Возвращает страницу просмотренных фильмов с пагинацией и фильтрами.
     *
     * @param pageable   пагинация и сортировка
     * @param search     поиск по названию (может быть null)
     * @param censorship фильтр по цензурному рейтингу (может быть null)
     * @param userId     текущий пользователь для его оценки
     * @return страница фильмов
     */
    @Transactional(readOnly = true)
    public Page<FilmDto> getArchive(Pageable pageable, String search, CensorshipRating censorship, Long userId) {
        String normalizedSearch = (search == null || search.isBlank()) ? null : search.trim();
        Page<Film> films = filmRepository.findWatched(FilmStatus.WATCHED, normalizedSearch, censorship, pageable);
        Map<Long, String> proposers = loadProposers(films.getContent());
        return films.map(film -> {
            FilmDto dto = filmMapper.toDto(film);
            RatingStatsDto stats = getFilmStats(film.getId());
            dto.setAverageRating(stats.getAverage());
            dto.setTotalVotes(stats.getTotalVotes());
            dto.setUserRating(getUserRating(film.getId(), userId));
            dto.setProposerUsername(proposers.get(film.getUserId()));
            dto.setProposerId(film.getUserId());
            return dto;
        });
    }

    /**
     * Возвращает статистику оценок просмотренных фильмов, предложенных пользователем.
     *
     * @param userId идентификатор пользователя
     * @return статистика: средняя оценка и количество просмотренных фильмов
     */
    @Transactional(readOnly = true)
    public RatingStatsDto getUserFilmsStats(Long userId) {
        long filmCount = filmRepository.countByUserIdAndStatus(userId, FilmStatus.WATCHED);
        if (filmCount == 0) {
            return new RatingStatsDto(0.0, 0, new LinkedHashMap<>());
        }
        Double average = ratingRepository.averageRatingForUserFilms(userId);
        return new RatingStatsDto(average == null ? 0.0 : normalizeToDisplay(average), filmCount, new LinkedHashMap<>());
    }

    /**
     * Возвращает испорченность пользователя — средний рейтинг цензуры его просмотренных фильмов.
     *
     * @param userId идентификатор пользователя
     * @return средняя испорченность от 0 до 4 или null, если фильмов с цензурой нет
     */
    @Transactional(readOnly = true)
    public Double getUserCorruption(Long userId) {
        List<Integer> values = filmRepository.findByUserIdAndStatus(userId, FilmStatus.WATCHED).stream()
                .map(Film::getCensorship)
                .filter(Objects::nonNull)
                .map(CensorshipRating::ordinal)
                .toList();
        if (values.isEmpty()) {
            return null;
        }
        return values.stream().mapToInt(Integer::intValue).average().orElse(0.0);
    }

    /**
     * Возвращает топ предлагающих пользователей по качеству предложенных фильмов.
     *
     * @param limit размер топа
     * @return список пользователей, отсортированный по убыванию средней оценки
     */
    @Transactional(readOnly = true)
    public List<ProposerStatDto> getTopProposers(int limit) {
        return allProposerStats().stream()
                .filter(dto -> dto.getAverageRating() != null)
                .sorted(Comparator.comparingDouble(ProposerStatDto::getAverageRating).reversed()
                        .thenComparing(ProposerStatDto::getUsername))
                .limit(limit)
                .toList();
    }

    /**
     * Возвращает топ предлагающих пользователей по испорченности предложенных фильмов.
     *
     * @param limit размер топа
     * @return список пользователей, отсортированный по убыванию испорченности
     */
    @Transactional(readOnly = true)
    public List<ProposerStatDto> getTopCorruption(int limit) {
        return allProposerStats().stream()
                .filter(dto -> dto.getCorruption() != null)
                .sorted(Comparator.comparingDouble(ProposerStatDto::getCorruption).reversed()
                        .thenComparing(ProposerStatDto::getUsername))
                .limit(limit)
                .toList();
    }

    /**
     * Возвращает публичный профиль пользователя кинотеатра.
     *
     * @param ownerId  владелец профиля
     * @param viewerId текущий пользователь (может быть null)
     * @return данные публичного профиля
     * @throws BusinessException если пользователь не найден
     */
    @Transactional(readOnly = true)
    public PublicProfileDto getPublicProfile(Long ownerId, Long viewerId) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Double averageRating = ratingRepository.averageRatingForUserFilms(ownerId);
        Double corruption = getUserCorruption(ownerId);
        Double viewerToOwner = viewerId == null
                ? null : ratingRepository.averageRatingByUserOnUserFilms(viewerId, ownerId);
        Double ownerToViewer = viewerId == null
                ? null : ratingRepository.averageRatingByUserOnUserFilms(ownerId, viewerId);
        return new PublicProfileDto(owner.getId(), owner.getUsername(), owner.getRole(),
                owner.getCreatedAt(),
                averageRating == null ? null : normalizeToDisplay(averageRating),
                corruption,
                viewerToOwner == null ? null : normalizeToDisplay(viewerToOwner),
                ownerToViewer == null ? null : normalizeToDisplay(ownerToViewer));
    }

    private List<ProposerStatDto> allProposerStats() {
        List<Film> watched = filmRepository.findAllByStatus(FilmStatus.WATCHED);
        if (watched.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Film>> byUser = watched.stream().collect(Collectors.groupingBy(Film::getUserId));
        Map<Long, String> usernames = userRepository.findByIdIn(byUser.keySet()).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
        Map<Long, List<FilmRating>> ratingsByFilm = ratingRepository.findAll().stream()
                .collect(Collectors.groupingBy(FilmRating::getFilmId));

        return byUser.entrySet().stream().map(entry -> {
            ProposerStatDto dto = new ProposerStatDto();
            dto.setUserId(entry.getKey());
            dto.setUsername(usernames.getOrDefault(entry.getKey(), String.valueOf(entry.getKey())));
            dto.setWatchedCount(entry.getValue().size());
            List<Integer> ratings = entry.getValue().stream()
                    .flatMap(film -> ratingsByFilm.getOrDefault(film.getId(), List.of()).stream())
                    .map(FilmRating::getRating)
                    .toList();
            if (!ratings.isEmpty()) {
                dto.setAverageRating(normalizeToDisplay(ratings.stream().mapToInt(Integer::intValue).average().orElse(0.0)));
            }
            List<Integer> censors = entry.getValue().stream()
                    .map(Film::getCensorship)
                    .filter(Objects::nonNull)
                    .map(CensorshipRating::ordinal)
                    .toList();
            if (!censors.isEmpty()) {
                dto.setCorruption(censors.stream().mapToInt(Integer::intValue).average().orElse(0.0));
            }
            return dto;
        }).toList();
    }

    private Map<Long, String> loadProposers(List<Film> films) {
        List<Long> userIds = films.stream().map(Film::getUserId).distinct().toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findByIdIn(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
    }

    private void requireWatched(Long filmId) {
        Film film = filmRepository.findById(filmId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILM_NOT_FOUND));
        if (film.getStatus() != FilmStatus.WATCHED) {
            throw new BusinessException(ErrorCode.FILM_NOT_WATCHED);
        }
    }

    /**
     * Нормирует среднюю оценку с 4-балльной шкалы (0–3) на 10-балльную для отображения:
     * среднее оценок / 3 * 10 (с округлением до сотых).
     *
     * @param rawAverage средняя оценка по шкале 0–3
     * @return значение для отображения по шкале 0–10
     */
    private double normalizeToDisplay(double rawAverage) {
        return Math.round(rawAverage / RATING_SCALE_STEPS * DISPLAY_SCALE * 100.0) / 100.0;
    }

    private void validateRating(Integer rating) {
        if (rating == null || rating < MIN_RATING || rating > MAX_RATING) {
            throw new BusinessException(ErrorCode.INVALID_RATING);
        }
    }
}