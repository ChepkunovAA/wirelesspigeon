package org.corpsman.cinema.service;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RatingServiceTest {

    @Mock
    private FilmRatingRepository ratingRepository;

    @Mock
    private FilmRepository filmRepository;

    @Mock
    private FilmMapper filmMapper;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RatingService ratingService;

    @Test
    void rateFilmCreatesNewRatingWhenNoneExists() {
        Film film = watchedFilm(1L);
        when(filmRepository.findById(1L)).thenReturn(Optional.of(film));
        when(ratingRepository.findByFilmIdAndUserId(1L, 2L)).thenReturn(Optional.empty());

        ratingService.rateFilm(1L, 2L, 3, "Отлично");

        ArgumentCaptor<FilmRating> captor = ArgumentCaptor.forClass(FilmRating.class);
        verify(ratingRepository).save(captor.capture());
        assertThat(captor.getValue().getFilmId()).isEqualTo(1L);
        assertThat(captor.getValue().getUserId()).isEqualTo(2L);
        assertThat(captor.getValue().getRating()).isEqualTo(3);
        assertThat(captor.getValue().getReview()).isEqualTo("Отлично");
    }

    @Test
    void rateFilmUpdatesExistingRating() {
        Film film = watchedFilm(1L);
        FilmRating existing = new FilmRating();
        existing.setFilmId(1L);
        existing.setUserId(2L);
        existing.setRating(1);
        when(filmRepository.findById(1L)).thenReturn(Optional.of(film));
        when(ratingRepository.findByFilmIdAndUserId(1L, 2L)).thenReturn(Optional.of(existing));

        ratingService.rateFilm(1L, 2L, 3, null);

        verify(ratingRepository).save(existing);
        assertThat(existing.getRating()).isEqualTo(3);
        verify(ratingRepository, times(1)).save(any(FilmRating.class));
    }

    @Test
    void rateFilmThrowsWhenFilmNotWatched() {
        Film film = new Film();
        film.setStatus(FilmStatus.PROPOSED);
        when(filmRepository.findById(1L)).thenReturn(Optional.of(film));

        assertThatThrownBy(() -> ratingService.rateFilm(1L, 2L, 3, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FILM_NOT_WATCHED);
    }

    @Test
    void rateFilmThrowsForOutOfRangeRating() {
        when(filmRepository.findById(1L)).thenReturn(Optional.of(watchedFilm(1L)));

        assertThatThrownBy(() -> ratingService.rateFilm(1L, 2L, 11, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_RATING);
        verify(ratingRepository, never()).save(any(FilmRating.class));
    }

    @Test
    void rateFilmAllowsZeroRating() {
        Film film = watchedFilm(1L);
        when(filmRepository.findById(1L)).thenReturn(Optional.of(film));
        when(ratingRepository.findByFilmIdAndUserId(1L, 2L)).thenReturn(Optional.empty());

        ratingService.rateFilm(1L, 2L, 0, null);

        ArgumentCaptor<FilmRating> captor = ArgumentCaptor.forClass(FilmRating.class);
        verify(ratingRepository).save(captor.capture());
        assertThat(captor.getValue().getRating()).isZero();
    }

    @Test
    void deleteRatingRemovesUserRating() {
        ratingService.deleteRating(1L, 2L);

        verify(ratingRepository).deleteByFilmIdAndUserId(1L, 2L);
    }

    @Test
    void getUserRatingReturnsNullWhenAbsent() {
        when(ratingRepository.findByFilmIdAndUserId(1L, 2L)).thenReturn(Optional.empty());

        assertThat(ratingService.getUserRating(1L, 2L)).isNull();
    }

    @Test
    void getFilmStatsComputesAverageAndDistribution() {
        when(ratingRepository.findByFilmId(1L)).thenReturn(List.of(
                rating(1L, 1L, 0),
                rating(1L, 3L, 1),
                rating(1L, 4L, 2)));

        RatingStatsDto stats = ratingService.getFilmStats(1L);

        assertThat(stats.getTotalVotes()).isEqualTo(3);
        assertThat(stats.getAverage()).isEqualTo(3.33);
        assertThat(stats.getDistribution()).containsEntry(0, 1L).containsEntry(1, 1L).containsEntry(2, 1L);
    }

    @Test
    void getFilmStatsReturnsEmptyWhenNoRatings() {
        when(ratingRepository.findByFilmId(1L)).thenReturn(List.of());

        RatingStatsDto stats = ratingService.getFilmStats(1L);

        assertThat(stats.getTotalVotes()).isZero();
        assertThat(stats.getAverage()).isZero();
        assertThat(stats.getDistribution()).isEmpty();
    }

    @Test
    void getArchivePopulatesProposerUsername() {
        Film film = watchedFilm(1L);
        film.setUserId(5L);
        when(filmRepository.findWatched(FilmStatus.WATCHED, null, null, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(film)));
        when(ratingRepository.findByFilmId(1L)).thenReturn(List.of());
        when(ratingRepository.findByFilmIdAndUserId(1L, 7L)).thenReturn(Optional.empty());
        User proposer = new User();
        proposer.setId(5L);
        proposer.setUsername("alice");
        when(userRepository.findByIdIn(List.of(5L))).thenReturn(List.of(proposer));
        when(filmMapper.toDto(film)).thenReturn(new FilmDto());

        Page<FilmDto> page = ratingService.getArchive(PageRequest.of(0, 20), null, null, 7L);

        assertThat(page.getContent()).singleElement().satisfies(dto ->
                assertThat(dto.getProposerUsername()).isEqualTo("alice"));
        verify(userRepository).findByIdIn(List.of(5L));
    }

    @Test
    void getUserFilmsStatsReturnsAverage() {
        when(filmRepository.countByUserIdAndStatus(2L, FilmStatus.WATCHED)).thenReturn(2L);
        when(ratingRepository.averageRatingForUserFilms(2L)).thenReturn(2.0);

        RatingStatsDto stats = ratingService.getUserFilmsStats(2L);

        assertThat(stats.getAverage()).isEqualTo(6.67);
        assertThat(stats.getTotalVotes()).isEqualTo(2);
    }

    @Test
    void getUserFilmsStatsReturnsEmptyWhenNoWatchedFilms() {
        when(filmRepository.countByUserIdAndStatus(2L, FilmStatus.WATCHED)).thenReturn(0L);

        RatingStatsDto stats = ratingService.getUserFilmsStats(2L);

        assertThat(stats.getAverage()).isZero();
        assertThat(stats.getTotalVotes()).isZero();
        verify(ratingRepository, never()).averageRatingForUserFilms(2L);
    }

    @Test
    void getUserCorruptionReturnsAverageCensorship() {
        Film erotica = watchedFilm(1L);
        erotica.setCensorship(CensorshipRating.EROTICA);
        Film nightmare = watchedFilm(2L);
        nightmare.setCensorship(CensorshipRating.NIGHTMARE);
        when(filmRepository.findByUserIdAndStatus(5L, FilmStatus.WATCHED)).thenReturn(List.of(erotica, nightmare));

        assertThat(ratingService.getUserCorruption(5L)).isEqualTo(3.0);
    }

    @Test
    void getUserCorruptionReturnsNullWhenNoCensorship() {
        when(filmRepository.findByUserIdAndStatus(5L, FilmStatus.WATCHED))
                .thenReturn(List.of(watchedFilm(1L)));

        assertThat(ratingService.getUserCorruption(5L)).isNull();
    }

    @Test
    void getTopProposersSortsByAverageRating() {
        Film aliceFilm = watchedFilm(1L);
        aliceFilm.setUserId(5L);
        Film bobFilm = watchedFilm(2L);
        bobFilm.setUserId(6L);
        when(filmRepository.findAllByStatus(FilmStatus.WATCHED)).thenReturn(List.of(aliceFilm, bobFilm));
        when(userRepository.findByIdIn(anySet())).thenReturn(List.of(user(5L, "alice"), user(6L, "bob")));
        when(ratingRepository.findAll()).thenReturn(List.of(
                rating(1L, 1L, 2), rating(1L, 2L, 3), rating(2L, 1L, 1)));

        List<ProposerStatDto> top = ratingService.getTopProposers(10);

        assertThat(top).extracting(ProposerStatDto::getUsername).containsExactly("alice", "bob");
        assertThat(top.get(0).getAverageRating()).isEqualTo(8.33);
        assertThat(top.get(1).getAverageRating()).isEqualTo(3.33);
        assertThat(top).allSatisfy(dto -> assertThat(dto.getWatchedCount()).isEqualTo(1));
    }

    @Test
    void getTopCorruptionSortsByCensorship() {
        Film aliceChaste = watchedFilm(1L);
        aliceChaste.setUserId(5L);
        aliceChaste.setCensorship(CensorshipRating.CHASTE);
        Film aliceErotica = watchedFilm(3L);
        aliceErotica.setUserId(5L);
        aliceErotica.setCensorship(CensorshipRating.EROTICA);
        Film bobNightmare = watchedFilm(2L);
        bobNightmare.setUserId(6L);
        bobNightmare.setCensorship(CensorshipRating.NIGHTMARE);
        when(filmRepository.findAllByStatus(FilmStatus.WATCHED)).thenReturn(List.of(aliceChaste, bobNightmare, aliceErotica));
        when(userRepository.findByIdIn(anySet())).thenReturn(List.of(user(5L, "alice"), user(6L, "bob")));
        when(ratingRepository.findAll()).thenReturn(List.of());

        List<ProposerStatDto> top = ratingService.getTopCorruption(10);

        assertThat(top).extracting(ProposerStatDto::getUsername).containsExactly("bob", "alice");
        assertThat(top.get(0).getCorruption()).isEqualTo(4.0);
        assertThat(top.get(1).getCorruption()).isEqualTo(1.0);
    }

    @Test
    void getPublicProfileReturnsUserDataAndAggregates() {
        User owner = user(5L, "alice");
        when(userRepository.findById(5L)).thenReturn(Optional.of(owner));
        when(ratingRepository.averageRatingForUserFilms(5L)).thenReturn(2.0);
        when(ratingRepository.averageRatingByUserOnUserFilms(7L, 5L)).thenReturn(1.0);
        when(ratingRepository.averageRatingByUserOnUserFilms(5L, 7L)).thenReturn(3.0);
        Film erotica = watchedFilm(1L);
        erotica.setCensorship(CensorshipRating.EROTICA);
        Film nightmare = watchedFilm(2L);
        nightmare.setCensorship(CensorshipRating.NIGHTMARE);
        when(filmRepository.findByUserIdAndStatus(5L, FilmStatus.WATCHED))
                .thenReturn(List.of(erotica, nightmare));

        PublicProfileDto profile = ratingService.getPublicProfile(5L, 7L);

        assertThat(profile.getUsername()).isEqualTo("alice");
        assertThat(profile.getAverageRating()).isEqualTo(6.67);
        assertThat(profile.getCorruption()).isEqualTo(3.0);
        assertThat(profile.getAverageViewerToOwner()).isEqualTo(3.33);
        assertThat(profile.getAverageOwnerToViewer()).isEqualTo(10.0);
    }

    @Test
    void getPublicProfileThrowsWhenUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ratingService.getPublicProfile(99L, 7L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    private User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }

    private Film watchedFilm(Long id) {
        Film film = new Film();
        film.setId(id);
        film.setTitle("Матрица");
        film.setYear(1999);
        film.setUserId(1L);
        film.setStatus(FilmStatus.WATCHED);
        return film;
    }

    private FilmRating rating(Long filmId, Long userId, int value) {
        FilmRating rating = new FilmRating();
        rating.setFilmId(filmId);
        rating.setUserId(userId);
        rating.setRating(value);
        return rating;
    }
}