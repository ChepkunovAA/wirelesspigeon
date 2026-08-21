package org.corpsman.cinema.config;

import lombok.extern.slf4j.Slf4j;
import org.corpsman.common.event.FilmAddedEvent;
import org.corpsman.common.event.FilmSelectedEvent;
import org.corpsman.common.event.FilmWatchedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Подписчик на события кинотеатра.
 */
@Component
@Slf4j
public class CinemaEventListener {

    /**
     * Обрабатывает событие добавления фильма.
     *
     * @param event событие добавления
     */
    @Async("corpsmanTaskExecutor")
    @EventListener
    public void onFilmAdded(FilmAddedEvent event) {
        log.debug("Обработка события добавления фильма, filmId={}, title={}", event.getFilmId(), event.getFilmTitle());
    }

    /**
     * Обрабатывает событие выбора фильма.
     *
     * @param event событие выбора
     */
    @Async("corpsmanTaskExecutor")
    @EventListener
    public void onFilmSelected(FilmSelectedEvent event) {
        log.debug("Обработка события выбора фильма, filmId={}", event.getFilmId());
    }

    /**
     * Обрабатывает событие просмотра фильма.
     *
     * @param event событие просмотра
     */
    @Async("corpsmanTaskExecutor")
    @EventListener
    public void onFilmWatched(FilmWatchedEvent event) {
        log.debug("Обработка события просмотра фильма, filmId={}", event.getFilmId());
    }
}