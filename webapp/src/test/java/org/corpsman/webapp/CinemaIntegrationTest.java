package org.corpsman.webapp;

import org.corpsman.auth.model.User;
import org.corpsman.auth.model.UserRole;
import org.corpsman.auth.repository.UserRepository;
import org.corpsman.cinema.model.Film;
import org.corpsman.cinema.model.FilmStatus;
import org.corpsman.cinema.repository.FilmRatingRepository;
import org.corpsman.cinema.repository.FilmRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CinemaIntegrationTest {

    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FilmRepository filmRepository;

    @Autowired
    private FilmRatingRepository ratingRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void clearCaches() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @Test
    void userProposesFilmAndSeesItInList() throws Exception {
        createUser("auto_cinema_alice_1");
        MockHttpSession session = login("auto_cinema_alice_1");

        mockMvc.perform(post("/cinema/add").session(session).with(csrf())
                        .param("title", "Матрица"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cinema"));

        mockMvc.perform(get("/cinema").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Матрица")));
    }

    @Test
    void duplicateProposalIsRejected() throws Exception {
        User alice = createUser("auto_cinema_alice_2");
        MockHttpSession session = login("auto_cinema_alice_2");
        propose(alice, "Дюна", 2021);

        mockMvc.perform(post("/cinema/add").session(session).with(csrf())
                        .param("title", "Дюна"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Вы уже предлагали этот фильм")));
    }

    @Test
    void adminSelectsFilmThenMarksAsWatched() throws Exception {
        User alice = createUser("auto_cinema_alice_3");
        createAdmin("auto_cinema_admin_3");
        propose(alice, "Интерстеллар", 2014);
        MockHttpSession adminSession = login("auto_cinema_admin_3");

        mockMvc.perform(post("/admin/cinema/select").session(adminSession).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cinema"))
                .andExpect(flash().attributeExists("success"));

        Film selected = filmRepository.findByStatus(FilmStatus.SELECTED).orElseThrow();
        assertThat(selected.getStatus()).isEqualTo(FilmStatus.SELECTED);

        mockMvc.perform(get("/cinema").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Интерстеллар")));

        mockMvc.perform(post("/admin/cinema/mark-watched").session(adminSession).with(csrf())
                        .param("filmId", String.valueOf(selected.getId())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cinema"))
                .andExpect(flash().attributeExists("success"));

        Film watched = filmRepository.findById(selected.getId()).orElseThrow();
        assertThat(watched.getStatus()).isEqualTo(FilmStatus.WATCHED);
        assertThat(watched.getWatchedAt()).isNotNull();
    }

    @Test
    void selectingWhenAlreadySelectedShowsWarning() throws Exception {
        User alice = createUser("auto_cinema_alice_4");
        createAdmin("auto_cinema_admin_4");
        propose(alice, "Тёмный рыцарь", 2008);
        MockHttpSession session = login("auto_cinema_admin_4");

        mockMvc.perform(post("/admin/cinema/select").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/admin/cinema/select").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("warning"))
                .andExpect(flash().attributeExists("selectedFilmId"));
    }

    @Test
    void adminReplacesSelectedFilm() throws Exception {
        User alice = createUser("auto_cinema_alice_6");
        createAdmin("auto_cinema_admin_6");
        propose(alice, "Джон Уик", 2014);
        propose(alice, "Гравитация", 2013);
        MockHttpSession session = login("auto_cinema_admin_6");

        mockMvc.perform(post("/admin/cinema/select").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(filmRepository.findAllByStatus(FilmStatus.SELECTED)).hasSize(1);

        mockMvc.perform(post("/admin/cinema/select").session(session).with(csrf())
                        .param("replace", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cinema"))
                .andExpect(flash().attributeExists("success"));

        assertThat(filmRepository.findAllByStatus(FilmStatus.SELECTED)).hasSize(1);
        assertThat(filmRepository.findAllByStatus(FilmStatus.PROPOSED)).hasSize(1);
    }

    @Test
    void adminDiscardsSelectedFilmWhenReplacing() throws Exception {
        User alice = createUser("auto_cinema_alice_7");
        createAdmin("auto_cinema_admin_7");
        propose(alice, "Мстители", 2012);
        propose(alice, "Титаник", 1997);
        MockHttpSession session = login("auto_cinema_admin_7");

        mockMvc.perform(post("/admin/cinema/select").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(filmRepository.findAllByStatus(FilmStatus.SELECTED)).hasSize(1);

        mockMvc.perform(post("/admin/cinema/select").session(session).with(csrf())
                        .param("replace", "true")
                        .param("discard", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cinema"))
                .andExpect(flash().attributeExists("success"));

        assertThat(filmRepository.findAllByStatus(FilmStatus.SELECTED)).hasSize(1);
        assertThat(filmRepository.findAllByStatus(FilmStatus.DISCARDED)).hasSize(1);
        assertThat(filmRepository.findAllByStatus(FilmStatus.PROPOSED)).isEmpty();
    }

    @Test
    void statisticsPageShowsTopProposers() throws Exception {
        User alice = createUser("auto_cinema_alice_8");
        createAdmin("auto_cinema_admin_8");
        propose(alice, "Интерстеллар", 2014);
        MockHttpSession adminSession = login("auto_cinema_admin_8");

        mockMvc.perform(post("/admin/cinema/select").session(adminSession).with(csrf()));
        Film selected = filmRepository.findByStatus(FilmStatus.SELECTED).orElseThrow();
        mockMvc.perform(post("/admin/cinema/mark-watched").session(adminSession).with(csrf())
                        .param("filmId", String.valueOf(selected.getId())));
        mockMvc.perform(post("/admin/cinema/censorship").session(adminSession).with(csrf())
                        .param("filmId", String.valueOf(selected.getId()))
                        .param("rating", "NIGHTMARE"));
        MockHttpSession aliceSession = login("auto_cinema_alice_8");
        mockMvc.perform(post("/cinema/rate").session(aliceSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filmId\": " + selected.getId() + ", \"rating\": 3}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/cinema/stat").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Топ лучших предлагающих")))
                .andExpect(content().string(containsString("Топ испорченности предлагающих")))
                .andExpect(content().string(containsString("auto_cinema_alice_8")));
    }

    @Test
    void ratingIsUpsertedForWatchedFilm() throws Exception {
        User alice = createUser("auto_cinema_alice_5");
        createAdmin("auto_cinema_admin_5");
        propose(alice, "Бегущий по лезвию", 1982);
        MockHttpSession adminSession = login("auto_cinema_admin_5");

        mockMvc.perform(post("/admin/cinema/select").session(adminSession).with(csrf()))
                .andExpect(status().is3xxRedirection());
        Film selected = filmRepository.findByStatus(FilmStatus.SELECTED).orElseThrow();
        mockMvc.perform(post("/admin/cinema/mark-watched").session(adminSession).with(csrf())
                        .param("filmId", String.valueOf(selected.getId())))
                .andExpect(status().is3xxRedirection());

        MockHttpSession aliceSession = login("auto_cinema_alice_5");
        String base = "{\"filmId\": " + selected.getId();

        mockMvc.perform(post("/cinema/rate").session(aliceSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(base + ", \"rating\": 2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.average").value(6.67))
                .andExpect(jsonPath("$.totalVotes").value(1));

        mockMvc.perform(post("/cinema/rate").session(aliceSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(base + ", \"rating\": 3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(3))
                .andExpect(jsonPath("$.average").value(10.0));

        assertThat(ratingRepository.findByFilmId(selected.getId())).hasSize(1);
        assertThat(ratingRepository.findByFilmIdAndUserId(selected.getId(), alice.getId()).orElseThrow().getRating())
                .isEqualTo(3);
    }

    @Test
    void ratingRejectedForFilmThatIsNotWatched() throws Exception {
        User alice = createUser("auto_cinema_alice_6");
        propose(alice, "Матрица", 1999);
        MockHttpSession session = login("auto_cinema_alice_6");

        Film proposed = filmRepository.findByStatus(FilmStatus.PROPOSED).orElseThrow();
        mockMvc.perform(post("/cinema/rate").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filmId\": " + proposed.getId() + ", \"rating\": 3}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void nonAdminCannotSelectFilm() throws Exception {
        createUser("auto_cinema_alice_7");
        MockHttpSession session = login("auto_cinema_alice_7");

        mockMvc.perform(post("/admin/cinema/select").session(session).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void nonOwnerCannotDeleteFilm() throws Exception {
        User alice = createUser("auto_cinema_alice_8");
        createUser("auto_cinema_bob_8");
        Film film = propose(alice, "Гравитация", 2013);
        MockHttpSession bobSession = login("auto_cinema_bob_8");

        mockMvc.perform(post("/cinema/delete/" + film.getId()).session(bobSession).with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(filmRepository.findById(film.getId())).isPresent();
    }

    @Test
    void ownerCanDeleteProposedFilm() throws Exception {
        User alice = createUser("auto_cinema_alice_9");
        Film film = propose(alice, "Начало", 2010);
        MockHttpSession session = login("auto_cinema_alice_9");

        mockMvc.perform(post("/cinema/delete/" + film.getId()).session(session).with(csrf()))
                .andExpect(status().isOk());
        assertThat(filmRepository.findById(film.getId())).isEmpty();
    }

    @Test
    void watchedFilmAppearsInArchive() throws Exception {
        User alice = createUser("auto_cinema_alice_10");
        createAdmin("auto_cinema_admin_10");
        propose(alice, "Бойцовский клуб", 1999);
        MockHttpSession adminSession = login("auto_cinema_admin_10");

        mockMvc.perform(post("/admin/cinema/select").session(adminSession).with(csrf()))
                .andExpect(status().is3xxRedirection());
        Film selected = filmRepository.findByStatus(FilmStatus.SELECTED).orElseThrow();
        mockMvc.perform(post("/admin/cinema/mark-watched").session(adminSession).with(csrf())
                        .param("filmId", String.valueOf(selected.getId())))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/cinema/archive").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Бойцовский клуб")));
    }

    @Test
    void publicProfileShowsUserInfoAndCrossRatings() throws Exception {
        User alice = createUser("auto_cinema_alice_11");
        User admin = createAdmin("auto_cinema_admin_11");
        propose(alice, "Олдбой", 2003);
        MockHttpSession adminSession = login("auto_cinema_admin_11");

        mockMvc.perform(post("/admin/cinema/select").session(adminSession).with(csrf()));
        Film selected = filmRepository.findByStatus(FilmStatus.SELECTED).orElseThrow();
        mockMvc.perform(post("/admin/cinema/mark-watched").session(adminSession).with(csrf())
                .param("filmId", String.valueOf(selected.getId())));
        mockMvc.perform(post("/admin/cinema/censorship").session(adminSession).with(csrf())
                .param("filmId", String.valueOf(selected.getId()))
                .param("rating", "EROTICA"));
        mockMvc.perform(post("/cinema/rate").session(adminSession).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"filmId\": " + selected.getId() + ", \"rating\": 2}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/profile/" + alice.getId()).session(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("auto_cinema_alice_11")))
                .andExpect(content().string(containsString("Дата регистрации")))
                .andExpect(content().string(containsString("Средняя оценка вами его фильмов")))
                .andExpect(content().string(containsString("Средняя оценка им ваших фильмов")))
                .andExpect(content().string(containsString("Средняя оценка фильмов")))
                .andExpect(content().string(containsString("Средняя испорченность")));

        mockMvc.perform(get("/profile/" + admin.getId()).session(adminSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"));

        mockMvc.perform(get("/profile/999999").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Пользователь не найден")));
    }

    private User createUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(UserRole.RESIDENT);
        user.setActive(true);
        return userRepository.save(user);
    }

    private User createAdmin(String username) {
        User admin = createUser(username);
        admin.setRole(UserRole.MASTER);
        return userRepository.save(admin);
    }

    private MockHttpSession login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/login").with(csrf())
                        .param("username", username)
                        .param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private Film propose(User user, String title, int year) {
        Film film = new Film();
        film.setTitle(title);
        film.setYear(year);
        film.setUserId(user.getId());
        film.setStatus(FilmStatus.PROPOSED);
        return filmRepository.save(film);
    }
}