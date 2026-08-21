package org.corpsman.cinema.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.corpsman.auth.model.UserRole;
import org.corpsman.auth.security.CorpsmanUserDetails;
import org.corpsman.auth.security.SecurityUtils;
import org.corpsman.cinema.dto.CinemaDashboardDto;
import org.corpsman.cinema.dto.FilmDto;
import org.corpsman.cinema.dto.RatingDto;
import org.corpsman.cinema.dto.RatingStatsDto;
import org.corpsman.cinema.model.CensorshipRating;
import org.corpsman.cinema.service.CinemaService;
import org.corpsman.cinema.service.RatingService;
import org.corpsman.common.exception.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

/**
 * Контроллер кинотеатра для пользователей.
 */
@Controller
@RequestMapping("/cinema")
@RequiredArgsConstructor
@Slf4j
public class CinemaController {

    private static final int ARCHIVE_PAGE_SIZE = 20;
    private static final int TOP_SIZE = 10;

    private final CinemaService cinemaService;
    private final RatingService ratingService;

    /**
     * Отображает главную страницу кинотеатра.
     *
     * @param model модель представления
     * @return имя представления
     */
    @GetMapping
    public String dashboard(Model model) {
        Long userId = SecurityUtils.currentUserId();
        CinemaDashboardDto dashboard = cinemaService.getDashboard();
        model.addAttribute("dashboard", buildView(dashboard, userId));
        model.addAttribute("myFilms", cinemaService.getUserFilms(userId));
        return "cinema/dashboard";
    }

    /**
     * Отображает статистику кинотеатра.
     *
     * @param model модель представления
     * @return имя представления
     */
    @GetMapping("/stat")
    public String statistics(Model model) {
        model.addAttribute("bestProposers", ratingService.getTopProposers(TOP_SIZE));
        model.addAttribute("corruptionTop", ratingService.getTopCorruption(TOP_SIZE));
        return "cinema/statistics";
    }

    /**
     * Отображает форму добавления фильма.
     *
     * @param model модель представления
     * @return имя представления
     */
    @GetMapping("/add")
    public String showAddForm(Model model) {
        model.addAttribute("film", new FilmDto());
        return "cinema/add-film";
    }

    /**
     * Добавляет новый фильм в предложения.
     *
     * @param dto    данные фильма
     * @param result результат валидации
     * @return редирект или форма с ошибками
     */
    @PostMapping("/add")
    public String addFilm(@Valid @ModelAttribute("film") FilmDto dto, BindingResult result) {
        if (result.hasErrors()) {
            return "cinema/add-film";
        }
        try {
            cinemaService.addFilm(dto, SecurityUtils.currentUserId());
        } catch (BusinessException ex) {
            result.reject("error", ex.getMessage());
            return "cinema/add-film";
        }
        return "redirect:/cinema";
    }

    /**
     * Удаляет фильм (AJAX).
     *
     * @param id идентификатор фильма
     * @return пустой ответ
     */
    @PostMapping("/delete/{id}")
    @ResponseBody
    public ResponseEntity<Void> deleteFilm(@PathVariable Long id) {
        cinemaService.deleteFilm(id, SecurityUtils.currentUserId(), isAdmin());
        return ResponseEntity.ok().build();
    }

    /**
     * Отображает архив просмотренных фильмов.
     *
     * @param page       номер страницы
     * @param search     поиск по названию
     * @param censorship фильтр по цензурному рейтингу
     * @param model      модель представления
     * @return имя представления
     */
    @GetMapping("/archive")
    public String archive(@RequestParam(defaultValue = "0") int page,
                          @RequestParam(required = false) String search,
                          @RequestParam(required = false) CensorshipRating censorship,
                          Model model) {
        PageRequest pageable = PageRequest.of(page, ARCHIVE_PAGE_SIZE, Sort.by("watchedAt").descending());
        model.addAttribute("page", ratingService.getArchive(pageable, search, censorship, SecurityUtils.currentUserId()));
        model.addAttribute("search", search);
        model.addAttribute("censorship", censorship);
        model.addAttribute("baseUrl", "/cinema/archive");
        return "cinema/archive";
    }

    /**
     * Ставит или обновляет оценку фильма (AJAX).
     *
     * @param dto данные оценки
     * @return результат оценки
     */
    @PostMapping("/rate")
    @ResponseBody
    public ResponseEntity<RatingDto> rate(@Valid @RequestBody RatingDto dto) {
        Long userId = SecurityUtils.currentUserId();
        ratingService.rateFilm(dto.getFilmId(), userId, dto.getRating(), dto.getReview());
        return ResponseEntity.ok(buildRatingResult(dto.getFilmId(), userId));
    }

    /**
     * Отзывает оценку фильма (AJAX).
     *
     * @param filmId идентификатор фильма
     * @return результат оценки
     */
    @DeleteMapping("/rate/{filmId}")
    @ResponseBody
    public ResponseEntity<RatingDto> deleteRating(@PathVariable Long filmId) {
        Long userId = SecurityUtils.currentUserId();
        ratingService.deleteRating(filmId, userId);
        return ResponseEntity.ok(buildRatingResult(filmId, userId));
    }

    private RatingDto buildRatingResult(Long filmId, Long userId) {
        RatingDto result = new RatingDto();
        result.setFilmId(filmId);
        Integer rating = ratingService.getUserRating(filmId, userId);
        result.setRating(rating);
        result.setUserVoted(rating != null);
        RatingStatsDto stats = ratingService.getFilmStats(filmId);
        result.setAverage(stats.getAverage());
        result.setTotalVotes(stats.getTotalVotes());
        return result;
    }

    private CinemaDashboardDto buildView(CinemaDashboardDto cached, Long userId) {
        CinemaDashboardDto view = new CinemaDashboardDto();
        view.setNextFilm(cached.getNextFilm());
        view.setLastFilmStats(cached.getLastFilmStats());
        FilmDto lastFilm = cached.getLastFilm();
        if (lastFilm != null) {
            FilmDto copy = new FilmDto();
            copy.setId(lastFilm.getId());
            copy.setTitle(lastFilm.getTitle());
            copy.setYear(lastFilm.getYear());
            copy.setTrailerUrl(lastFilm.getTrailerUrl());
            copy.setStatus(lastFilm.getStatus());
            copy.setWatchedAt(lastFilm.getWatchedAt());
            copy.setCensorship(lastFilm.getCensorship());
            copy.setUserRating(ratingService.getUserRating(lastFilm.getId(), userId));
            view.setLastFilm(copy);
        }
        return view;
    }

    private boolean isAdmin() {
        CorpsmanUserDetails user = SecurityUtils.currentUser();
        return user != null && user.getRole() == UserRole.ADMIN;
    }
}