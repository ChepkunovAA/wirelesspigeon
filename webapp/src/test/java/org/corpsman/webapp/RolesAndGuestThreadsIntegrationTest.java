package org.corpsman.webapp;

import org.corpsman.auth.model.User;
import org.corpsman.auth.model.UserRole;
import org.corpsman.auth.repository.UserRepository;
import org.corpsman.forum.model.ForumTopic;
import org.corpsman.forum.model.TopicVisibility;
import org.corpsman.forum.repository.ForumTopicRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RolesAndGuestThreadsIntegrationTest {

    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ForumTopicRepository topicRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void guestSeesPublicAndGuestTopicsButNotOpen() throws Exception {
        User resident = createUser("rg_resident_1", UserRole.RESIDENT);
        createTopic(resident, "Публичный rg_p1", TopicVisibility.PUBLIC);
        createTopic(resident, "Гостевой rg_g1", TopicVisibility.GUEST);
        createTopic(resident, "Открытый rg_o1", TopicVisibility.OPEN);
        MockHttpSession session = login("rg_resident_1");

        // резидент видит все три
        String residentPage = mockMvc.perform(get("/forum").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(residentPage).contains("rg_p1", "rg_g1", "rg_o1");

        // гость не видит открытый
        createUser("rg_guest_1", UserRole.GUEST);
        MockHttpSession guestSession = login("rg_guest_1");
        String guestPage = mockMvc.perform(get("/forum").session(guestSession))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(guestPage).contains("rg_p1", "rg_g1").doesNotContain("rg_o1");
        assertThat(guestPage).contains("Гостевой"); // маркер типа треда

        // прямой переход на открытый тред запрещён
        Long openId = topicRepository.findAll().stream()
                .filter(t -> t.getTitle().equals("Открытый rg_o1")).findFirst().orElseThrow().getId();
        mockMvc.perform(get("/forum/topic/" + openId).session(guestSession))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void guestWritesOnlyInGuestThreads() throws Exception {
        User resident = createUser("rg_resident_2", UserRole.RESIDENT);
        createTopic(resident, "Гостевой rg_g2", TopicVisibility.GUEST);
        createTopic(resident, "Открытый rg_o2", TopicVisibility.OPEN);
        createTopic(resident, "Публичный rg_p2", TopicVisibility.PUBLIC);
        createUser("rg_guest_2", UserRole.GUEST);
        MockHttpSession session = login("rg_guest_2");

        // гостевой — можно
        mockMvc.perform(post("/forum/topic/{id}/comment",
                        topicIdByTitle("Гостевой rg_g2")).session(session).with(csrf())
                        .param("content", "гостевой коммент"))
                .andExpect(status().is3xxRedirection());

        // открытый — нельзя, публичный — можно (изменение постановки)
        mockMvc.perform(post("/forum/topic/{id}/comment",
                        topicIdByTitle("Открытый rg_o2")).session(session).with(csrf())
                        .param("content", "попытка"))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(post("/forum/topic/{id}/comment",
                        topicIdByTitle("Публичный rg_p2")).session(session).with(csrf())
                        .param("content", "комментарий гостя в публичном треде"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void guestCreatesOnlyGuestThreads() throws Exception {
        createUser("rg_guest_3", UserRole.GUEST);
        MockHttpSession session = login("rg_guest_3");

        mockMvc.perform(post("/forum/topic/create").session(session).with(csrf())
                        .param("title", "Гостевой тред rg_t3")
                        .param("content", "Контент")
                        .param("visibility", "OPEN"))
                .andExpect(status().is3xxRedirection());

        ForumTopic created = topicRepository.findAll().stream()
                .filter(t -> t.getTitle().equals("Гостевой тред rg_t3")).findFirst().orElseThrow();
        assertThat(created.getVisibility()).isEqualTo(TopicVisibility.GUEST);

        MockHttpSession session2 = login("rg_guest_3");
        mockMvc.perform(get("/forum/topic/" + created.getId()).session(session2))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Гостевой")));
        assertThat(created.isReadOnly()).isFalse();
    }

    @Test
    void guestCannotFreezeResidentGuestThread() throws Exception {
        User resident = createUser("rg_resident_4", UserRole.RESIDENT);
        createTopic(resident, "Гостевой на модерацию rg_g4", TopicVisibility.GUEST);
        createUser("rg_guest_4", UserRole.GUEST);
        MockHttpSession session = login("rg_guest_4");
        Long tid = topicIdByTitle("Гостевой на модерацию rg_g4");

        // гость может модерировать только свои треды
        mockMvc.perform(post("/forum/topic/" + tid + "/moderation/freeze")
                        .session(session).with(csrf()))
                .andExpect(status().is4xxClientError());

        assertThat(topicRepository.findById(tid).orElseThrow().isFrozen()).isFalse();
    }

    @Test
    void masterCanModerateAnyTopic() throws Exception {
        User resident = createUser("rg_resident_4b", UserRole.RESIDENT);
        createTopic(resident, "Чужой тред для мастера rg_g4b", TopicVisibility.OPEN);
        User master = createUser("rg_master_4b", UserRole.MASTER);
        MockHttpSession session = login("rg_master_4b");
        Long tid = topicIdByTitle("Чужой тред для мастера rg_g4b");

        mockMvc.perform(post("/forum/topic/" + tid + "/moderation/freeze")
                        .session(session).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(topicRepository.findById(tid).orElseThrow().isFrozen()).isTrue();
    }

    @Test
    void readOnlyDefaultSettingPrefillsCreateForm() throws Exception {
        createUser("rg_user_4c", UserRole.RESIDENT);
        MockHttpSession session = login("rg_user_4c");

        mockMvc.perform(post("/settings/prefs").session(session).with(csrf())
                        .param("defaultReadOnly", "true"))
                .andExpect(status().is3xxRedirection());

        String page = mockMvc.perform(get("/forum/topic/create").session(session))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)
                .replaceAll("\\s+", " ");
        assertThat(page).contains("id=\"readOnly\" name=\"readOnly\" value=\"true\" checked=\"checked\"");
    }

    @Test
    void cinemaAndFilmDataHiddenFromGuest() throws Exception {
        createUser("rg_guest_5", UserRole.GUEST);
        MockHttpSession session = login("rg_guest_5");

        mockMvc.perform(get("/cinema").session(session))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/").session(session))
                .andExpect(content().string(not(containsString("Следующий фильм"))))
                .andExpect(content().string(containsString("Corpsman")));

        mockMvc.perform(get("/settings").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("defaultVisibility"))))
                .andExpect(content().string(containsString("defaultReadOnly")))
                .andExpect(content().string(containsString("defaultThreadAnonymous")));
    }

    @Test
    void masterPromotesGuestToResidentUnlockingCinema() throws Exception {
        User master = createUser("rg_master_6", UserRole.MASTER);
        User guest = createUser("rg_guest_6", UserRole.GUEST);
        MockHttpSession session = login("rg_master_6");

        mockMvc.perform(post("/users/" + guest.getId() + "/promote").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(userRepository.findById(guest.getId()).orElseThrow().getRole())
                .isEqualTo(UserRole.RESIDENT);

        // повышенный резидент получает доступ к кинотеатру
        mockMvc.perform(get("/cinema").session(login("rg_guest_6")))
                .andExpect(status().isOk());
    }

    @Test
    void keysPageScopedByRole() throws Exception {
        createUser("rg_resident_7", UserRole.RESIDENT);
        MockHttpSession resSession = login("rg_resident_7");

        // резидент создаёт гостевой ключ
        MvcResult gen = mockMvc.perform(post("/keys/generate").session(resSession).with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        String genPage = gen.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("Новый ключ[\\s\\S]*value=\"([a-f0-9-]{36})\"")
                .matcher(genPage);
        org.assertj.core.api.Assertions.assertThat(m.find()).isTrue();
        String rawToken = m.group(1);

        // регистрируем гостя этим ключом
        mockMvc.perform(post("/register").with(csrf())
                        .param("token", rawToken)
                        .param("username", "rg_newguest_7")
                        .param("password", PASSWORD))
                .andExpect(status().is3xxRedirection());

        User newbie = userRepository.findByUsername("rg_newguest_7").orElseThrow();
        assertThat(newbie.getRole()).isEqualTo(UserRole.GUEST);

        // гость не имеет доступа к странице ключей
        MockHttpSession guestSession = login("rg_newguest_7");
        mockMvc.perform(get("/keys").session(guestSession))
                .andExpect(status().isForbidden());

        // мастер видит все ключи, резидент — только свои
        User master = createUser("rg_master_7", UserRole.MASTER);
        MockHttpSession masterSession = login("rg_master_7");
        mockMvc.perform(post("/keys/generate").session(masterSession).with(csrf())) // мастерский (не гостевой)
                .andExpect(status().isOk());

        String masterKeys = mockMvc.perform(get("/keys").session(masterSession))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(masterKeys).contains("rg_resident_7"); // создатель гостевого ключа виден мастеру

        String residentKeys = mockMvc.perform(get("/keys").session(resSession))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)
                .replaceAll("\\s+", " ");
        // свой гостевой ключ виден, чужой (мастерский) — нет
        assertThat(countMatches(residentKeys, "гостевой")).isEqualTo(1); // только бейдж своего ключа
    }

    private int countMatches(String s, String sub) {
        int c = 0, i = 0;
        while ((i = s.indexOf(sub, i)) != -1) { c++; i += sub.length(); }
        return c;
    }

    private Long topicIdByTitle(String title) {
        return topicRepository.findAll().stream()
                .filter(t -> t.getTitle().equals(title)).findFirst().orElseThrow().getId();
    }

    private ForumTopic createTopic(User author, String title, TopicVisibility visibility) {
        ForumTopic topic = new ForumTopic();
        topic.setTitle(title);
        topic.setContent("Содержание: " + title);
        topic.setAuthorId(author.getId());
        topic.setVisibility(visibility);
        topic.setLastActivityAt(LocalDateTime.now());
        return topicRepository.save(topic);
    }

    private User createUser(String username, UserRole role) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(role);
        user.setActive(true);
        return userRepository.save(user);
    }

    private MockHttpSession login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/login").with(csrf())
                        .param("username", username)
                        .param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    void guestsCannotModerateOtherGuestsThreads() throws Exception {
        User guestOwner = createUser("rg_guest_owner_16", UserRole.GUEST);
        createTopic(guestOwner, "Гостевой тред другого гостя rg_g16", TopicVisibility.GUEST);
        createUser("rg_guest_16", UserRole.GUEST);
        MockHttpSession session = login("rg_guest_16");
        Long tid = topicIdByTitle("Гостевой тред другого гостя rg_g16");

        mockMvc.perform(post("/forum/topic/" + tid + "/moderation/freeze")
                        .session(session).with(csrf()))
                .andExpect(status().is4xxClientError());

        assertThat(topicRepository.findById(tid).orElseThrow().isFrozen()).isFalse();
    }

    @Test
    void cinemaSectionHiddenOnGuestProfiles() throws Exception {
        User resident = createUser("rg_resident_17", UserRole.RESIDENT);
        User guest = createUser("rg_guest_17", UserRole.GUEST);

        MockHttpSession resSession = login("rg_resident_17");
        mockMvc.perform(get("/profile/" + guest.getId()).session(resSession))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Кинотеатр"))))
                .andExpect(content().string(not(containsString("Средняя оценка вами его фильмов"))))
                .andExpect(content().string(not(containsString("Средняя оценка им ваших фильмов"))));

        MockHttpSession guestSession = login("rg_guest_17");
        mockMvc.perform(get("/profile/" + resident.getId()).session(guestSession))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Кинотеатр"))));
    }
}
