package org.corpsman.cinema.service;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CinemaServiceTest {

    @Mock
    private FilmRepository filmRepository;

    @Mock
    private FilmMapper filmMapper;

    @Mock
    private RatingService ratingService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private CinemaService cinemaService;

    @Test
    void addFilmCreatesProposedFilmAndPublishesEvent() {
        FilmDto dto = dto("Матрица", 1999);
        when(filmRepository.existsByUserIdAndTitleIgnoreCaseAndStatus(1L, "Матрица", FilmStatus.PROPOSED))
                .thenReturn(false);
        when(filmRepository.save(any(Film.class))).thenAnswer(invocation -> {
            Film film = invocation.getArgument(0);
            film.setId(10L);
            return film;
        });
        when(filmMapper.toDto(any(Film.class))).thenReturn(new FilmDto());

        cinemaService.addFilm(dto, 1L);

        ArgumentCaptor<Film> captor = ArgumentCaptor.forClass(Film.class);
        verify(filmRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("Матрица");
        assertThat(captor.getValue().getYear()).isEqualTo(1999);
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
        assertThat(captor.getValue().getStatus()).isEqualTo(FilmStatus.PROPOSED);
        verify(eventPublisher).publishEvent(any(FilmAddedEvent.class));
    }

    @Test
    void addFilmThrowsWhenDuplicateExists() {
        FilmDto dto = dto("Матрица", 1999);
        when(filmRepository.existsByUserIdAndTitleIgnoreCaseAndStatus(1L, "Матрица", FilmStatus.PROPOSED))
                .thenReturn(true);

        assertThatThrownBy(() -> cinemaService.addFilm(dto, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FILM_ALREADY_EXISTS);
        verify(filmRepository, never()).save(any(Film.class));
    }

    @Test
    void addFilmThrowsForFutureYear() {
        FilmDto dto = dto("Матрица", LocalDate.now().getYear() + 1);

        assertThatThrownBy(() -> cinemaService.addFilm(dto, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void deleteFilmByOwnerRemoves() {
        Film film = film(1L, 1L, FilmStatus.PROPOSED);
        when(filmRepository.findById(1L)).thenReturn(Optional.of(film));

        cinemaService.deleteFilm(1L, 1L, false);

        verify(filmRepository).delete(film);
    }

    @Test
    void deleteFilmForbiddenForOtherUser() {
        Film film = film(1L, 1L, FilmStatus.PROPOSED);
        when(filmRepository.findById(1L)).thenReturn(Optional.of(film));

        assertThatThrownBy(() -> cinemaService.deleteFilm(1L, 2L, false))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void deleteFilmForbiddenWhenSelected() {
        Film film = film(1L, 1L, FilmStatus.SELECTED);
        when(filmRepository.findById(1L)).thenReturn(Optional.of(film));

        assertThatThrownBy(() -> cinemaService.deleteFilm(1L, 1L, false))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FILM_CANNOT_DELETE);
    }

    @Test
    void deleteFilmByAdminAllows() {
        Film film = film(1L, 2L, FilmStatus.PROPOSED);
        when(filmRepository.findById(1L)).thenReturn(Optional.of(film));

        cinemaService.deleteFilm(1L, 99L, true);

        verify(filmRepository).delete(film);
    }

    @Test
    void selectNextFilmThrowsWhenAlreadySelected() {
        Film selected = film(1L, 1L, FilmStatus.SELECTED);
        when(filmRepository.findByStatus(FilmStatus.SELECTED)).thenReturn(Optional.of(selected));

        assertThatThrownBy(() -> cinemaService.selectNextFilm(false, false, 9L))
                .isInstanceOf(FilmAlreadySelectedException.class)
                .satisfies(ex -> assertThat(((FilmAlreadySelectedException) ex).getFilmTitle()).isEqualTo("Матрица"));
    }

    @Test
    void selectNextFilmThrowsWhenNoFilmsAvailable() {
        when(filmRepository.findByStatus(FilmStatus.SELECTED)).thenReturn(Optional.empty());
        when(filmRepository.findDistinctUserIdsByStatus(FilmStatus.PROPOSED)).thenReturn(List.of());

        assertThatThrownBy(() -> cinemaService.selectNextFilm(false, false, 9L))
                .isInstanceOf(NoFilmsAvailableException.class);
    }

    @Test
    void selectNextFilmMarksFilmSelected() {
        when(filmRepository.findByStatus(FilmStatus.SELECTED)).thenReturn(Optional.empty());
        when(filmRepository.findDistinctUserIdsByStatus(FilmStatus.PROPOSED)).thenReturn(List.of(7L));
        Film film = film(5L, 7L, FilmStatus.PROPOSED);
        when(filmRepository.findByUserIdAndStatus(7L, FilmStatus.PROPOSED)).thenReturn(List.of(film));
        when(filmMapper.toDto(any(Film.class))).thenReturn(new FilmDto());

        cinemaService.selectNextFilm(false, false, 9L);

        assertThat(film.getStatus()).isEqualTo(FilmStatus.SELECTED);
        verify(filmRepository).save(film);
        verify(eventPublisher).publishEvent(any(FilmSelectedEvent.class));
    }

    @Test
    void selectNextFilmWithReplaceRevertsPreviousSelection() {
        Film previous = film(1L, 1L, FilmStatus.SELECTED);
        when(filmRepository.findByStatus(FilmStatus.SELECTED)).thenReturn(Optional.of(previous));
        when(filmRepository.findDistinctUserIdsByStatus(FilmStatus.PROPOSED)).thenReturn(List.of(7L));
        Film newFilm = film(5L, 7L, FilmStatus.PROPOSED);
        when(filmRepository.findByUserIdAndStatus(7L, FilmStatus.PROPOSED)).thenReturn(List.of(newFilm));
        when(filmMapper.toDto(any(Film.class))).thenReturn(new FilmDto());

        cinemaService.selectNextFilm(true, false, 9L);

        assertThat(previous.getStatus()).isEqualTo(FilmStatus.PROPOSED);
        assertThat(newFilm.getStatus()).isEqualTo(FilmStatus.SELECTED);
        verify(filmRepository).save(previous);
        verify(filmRepository).save(newFilm);
    }

    @Test
    void selectNextFilmWithDiscardMarksPreviousDiscarded() {
        Film previous = film(1L, 1L, FilmStatus.SELECTED);
        when(filmRepository.findByStatus(FilmStatus.SELECTED)).thenReturn(Optional.of(previous));
        when(filmRepository.findDistinctUserIdsByStatus(FilmStatus.PROPOSED)).thenReturn(List.of(7L));
        Film newFilm = film(5L, 7L, FilmStatus.PROPOSED);
        when(filmRepository.findByUserIdAndStatus(7L, FilmStatus.PROPOSED)).thenReturn(List.of(newFilm));
        when(filmMapper.toDto(any(Film.class))).thenReturn(new FilmDto());

        cinemaService.selectNextFilm(true, true, 9L);

        assertThat(previous.getStatus()).isEqualTo(FilmStatus.DISCARDED);
        assertThat(newFilm.getStatus()).isEqualTo(FilmStatus.SELECTED);
        verify(filmRepository).save(previous);
        verify(filmRepository).save(newFilm);
    }

    @Test
    void selectNextFilmWithReplaceDoesNotRepickSameFilm() {
        Film previous = film(1L, 7L, FilmStatus.SELECTED);
        when(filmRepository.findByStatus(FilmStatus.SELECTED)).thenReturn(Optional.of(previous));
        when(filmRepository.findDistinctUserIdsByStatus(FilmStatus.PROPOSED)).thenReturn(List.of(7L));
        Film other = film(5L, 7L, FilmStatus.PROPOSED);
        when(filmRepository.findByUserIdAndStatus(7L, FilmStatus.PROPOSED)).thenReturn(List.of(previous, other));
        when(filmMapper.toDto(any(Film.class))).thenReturn(new FilmDto());

        cinemaService.selectNextFilm(true, false, 9L);

        assertThat(previous.getStatus()).isEqualTo(FilmStatus.PROPOSED);
        assertThat(other.getStatus()).isEqualTo(FilmStatus.SELECTED);
        verify(filmRepository).save(previous);
        verify(filmRepository).save(other);
    }

    @Test
    void selectNextFilmWithReplaceThrowsWhenNoOtherFilms() {
        Film previous = film(1L, 7L, FilmStatus.SELECTED);
        when(filmRepository.findByStatus(FilmStatus.SELECTED)).thenReturn(Optional.of(previous));
        when(filmRepository.findDistinctUserIdsByStatus(FilmStatus.PROPOSED)).thenReturn(List.of(7L));
        when(filmRepository.findByUserIdAndStatus(7L, FilmStatus.PROPOSED)).thenReturn(List.of(previous));

        assertThatThrownBy(() -> cinemaService.selectNextFilm(true, false, 9L))
                .isInstanceOf(NoFilmsAvailableException.class);
    }

    @Test
    void markAsWatchedRequiresSelectedStatus() {
        Film film = film(1L, 1L, FilmStatus.PROPOSED);
        when(filmRepository.findById(1L)).thenReturn(Optional.of(film));

        assertThatThrownBy(() -> cinemaService.markAsWatched(1L, 9L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FILM_NOT_SELECTED);
    }

    @Test
    void markAsWatchedSetsStatusAndDate() {
        Film film = film(1L, 1L, FilmStatus.SELECTED);
        when(filmRepository.findById(1L)).thenReturn(Optional.of(film));
        when(filmMapper.toDto(any(Film.class))).thenReturn(new FilmDto());

        cinemaService.markAsWatched(1L, 9L);

        assertThat(film.getStatus()).isEqualTo(FilmStatus.WATCHED);
        assertThat(film.getWatchedAt()).isNotNull();
        verify(eventPublisher).publishEvent(any(FilmWatchedEvent.class));
    }

    @Test
    void setCensorshipRequiresWatchedStatus() {
        Film film = film(1L, 1L, FilmStatus.SELECTED);
        when(filmRepository.findById(1L)).thenReturn(Optional.of(film));

        assertThatThrownBy(() -> cinemaService.setCensorship(1L, CensorshipRating.EROTICA))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FILM_NOT_WATCHED);
    }

    @Test
    void setCensorshipSetsRating() {
        Film film = film(1L, 1L, FilmStatus.WATCHED);
        when(filmRepository.findById(1L)).thenReturn(Optional.of(film));

        cinemaService.setCensorship(1L, CensorshipRating.EROTICA);

        assertThat(film.getCensorship()).isEqualTo(CensorshipRating.EROTICA);
        verify(filmRepository).save(film);
    }

    private Film film(Long id, Long userId, FilmStatus status) {
        Film film = new Film();
        film.setId(id);
        film.setTitle("Матрица");
        film.setYear(1999);
        film.setUserId(userId);
        film.setStatus(status);
        return film;
    }

    private FilmDto dto(String title, Integer year) {
        FilmDto dto = new FilmDto();
        dto.setTitle(title);
        dto.setYear(year);
        return dto;
    }
}