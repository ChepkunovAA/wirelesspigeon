package org.corpsman.cinema.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.corpsman.auth.security.SecurityUtils;
import org.corpsman.cinema.dto.FilmDto;
import org.corpsman.cinema.exception.FilmAlreadySelectedException;
import org.corpsman.cinema.exception.NoFilmsAvailableException;
import org.corpsman.cinema.model.CensorshipRating;
import org.corpsman.cinema.service.CinemaService;
import org.corpsman.common.exception.BusinessException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Административный контроллер кинотеатра.
 */
@Controller
@RequestMapping("/admin/cinema")
@RequiredArgsConstructor
@Slf4j
public class CinemaAdminController {

    private final CinemaService cinemaService;

    /**
     * Выбирает следующий фильм для просмотра.
     *
     * @param replace             заменить уже выбранный фильм
     * @param redirectAttributes атрибуты редиректа
     * @return редирект на главную страницу кинотеатра
     */
    @PostMapping("/select")
    public String selectNextFilm(@RequestParam(defaultValue = "false") boolean replace,
                                 @RequestParam(defaultValue = "false") boolean discard,
                                 RedirectAttributes redirectAttributes) {
        try {
            FilmDto selected = cinemaService.selectNextFilm(replace, discard, SecurityUtils.currentUserId());
            redirectAttributes.addFlashAttribute("success", "Выбран фильм: " + selected.getTitle());
        } catch (FilmAlreadySelectedException ex) {
            redirectAttributes.addFlashAttribute("warning",
                    "Фильм '" + ex.getFilmTitle() + "' уже выбран. Продолжить и заменить его?");
            redirectAttributes.addFlashAttribute("selectedFilmId", ex.getFilmId());
            log.warn("Фильм уже выбран: {}", ex.getFilmTitle());
        } catch (NoFilmsAvailableException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/cinema";
    }

    /**
     * Переводит выбранный фильм в просмотренные.
     *
     * @param filmId              идентификатор фильма
     * @param redirectAttributes атрибуты редиректа
     * @return редирект на главную страницу кинотеатра
     */
    @PostMapping("/mark-watched")
    public String markAsWatched(@RequestParam Long filmId, RedirectAttributes redirectAttributes) {
        try {
            FilmDto film = cinemaService.markAsWatched(filmId, SecurityUtils.currentUserId());
            redirectAttributes.addFlashAttribute("success", "Фильм '" + film.getTitle() + "' отмечен как просмотренный");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/cinema";
    }

    /**
     * Устанавливает цензурный рейтинг просмотренного фильма.
     *
     * @param filmId              идентификатор фильма
     * @param rating              цензурный рейтинг
     * @param redirectAttributes атрибуты редиректа
     * @return редирект на главную страницу кинотеатра
     */
    @PostMapping("/censorship")
    public String setCensorship(@RequestParam Long filmId,
                                @RequestParam CensorshipRating rating,
                                RedirectAttributes redirectAttributes) {
        try {
            cinemaService.setCensorship(filmId, rating);
            redirectAttributes.addFlashAttribute("success", "Цензурный рейтинг установлен: " + rating.getDisplayName());
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/cinema";
    }
}