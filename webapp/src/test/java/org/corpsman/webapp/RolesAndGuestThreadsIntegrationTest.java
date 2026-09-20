package org.corpsman.webapp;

import org.corpsman.auth.model.User;
import org.corpsman.auth.model.UserRole;
import org.corpsman.auth.repository.UserRepository;
import org.corpsman.forum.model.ForumTopic;
import org.corpsman.forum.model.TopicVisibility;
import org.corpsman.forum.repository.ForumTopicRepository;
import org.corpsman.forum.service.NotificationService;
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

    @Autowired
    private NotificationService notificationService;

    @Test
    void anonymousSeesForumNavLinkOnly() throws Exception {
        String home = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(home).contains("bi-chat-dots\"></i> Форум");
        assertThat(home).doesNotContain("Профиль");
        assertThat(home).contains("Войти");
        assertThat(home).doesNotContain("Участники");
    }

    @Test
    void notificationRowIsClickableAndRedirectsAfterRead() throws Exception {
        User resident = createUser("auto_rg_notif_1", UserRole.RESIDENT);
        MockHttpSession session = login("auto_rg_notif_1");
        notificationService.notify(resident.getId(), "TOPIC_COMMENT", "Новый комментарий", "/forum/topic/1");

        String page = mockMvc.perform(get("/notifications").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(page).contains("Новый комментарий");
        assertThat(page).contains("onclick=\"this.submit();\"");

        Long notifId = notificationService.listFor(resident.getId()).get(0).getId();
        mockMvc.perform(post("/notifications/{id}/read", notifId).session(session).with(csrf())
                        .param("to", "/forum/topic/1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/forum/topic/1"));
        assertThat(notificationService.listFor(resident.getId()).get(0).isRead()).isTrue();
    }

    @Test
    void guestSeesPublicAndGuestTopicsButNotOpen() throws Exception {
        User resident = createUser("auto_rg_resident_1", UserRole.RESIDENT);
        createTopic(resident, "Публичный rg_p1", TopicVisibility.PUBLIC);
        createTopic(resident, "Гостевой rg_g1", TopicVisibility.GUEST);
        createTopic(resident, "Открытый rg_o1", TopicVisibility.OPEN);
        MockHttpSession session = login("auto_rg_resident_1");

        // резидент видит все три
        String residentPage = mockMvc.perform(get("/forum").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(residentPage).contains("rg_p1", "rg_g1", "rg_o1");

        // гость не видит открытый
        createUser("auto_rg_guest_1", UserRole.GUEST);
        MockHttpSession guestSession = login("auto_rg_guest_1");
        String guestPage = mockMvc.perform(get("/forum").session(guestSession))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(guestPage).contains("rg_p1", "rg_g1").doesNotContain("rg_o1");
        // гость видит гостевые треды, но маркер «Гостевой» не видит (нет намёков на скрытый функционал)
        assertThat(guestPage).doesNotContain("text-bg-success");
        // резидент маркер видит
        assertThat(residentPage).contains("text-bg-success");

        // прямой переход на открытый тред запрещён
        Long openId = topicRepository.findAll().stream()
                .filter(t -> t.getTitle().equals("Открытый rg_o1")).findFirst().orElseThrow().getId();
        mockMvc.perform(get("/forum/topic/" + openId).session(guestSession))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void guestWritesOnlyInGuestThreads() throws Exception {
        User resident = createUser("auto_rg_resident_2", UserRole.RESIDENT);
        createTopic(resident, "Гостевой rg_g2", TopicVisibility.GUEST);
        createTopic(resident, "Открытый rg_o2", TopicVisibility.OPEN);
        createTopic(resident, "Публичный rg_p2", TopicVisibility.PUBLIC);
        createUser("auto_rg_guest_2", UserRole.GUEST);
        MockHttpSession session = login("auto_rg_guest_2");

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
    void guestCanCreateClosedAndGuestThreadsButNotPublic() throws Exception {
        createUser("auto_rg_guest_p2a", UserRole.GUEST);
        MockHttpSession session = login("auto_rg_guest_p2a");

        // гость создаёт «Закрытый» тред
        mockMvc.perform(post("/forum/topic/create").session(session).with(csrf())
                        .param("title", "Закрытый гостя rg_p2c")
                        .param("content", "Контент")
                        .param("visibility", "CLOSED"))
                .andExpect(status().is3xxRedirection());
        ForumTopic closed = topicRepository.findAll().stream()
                .filter(t -> t.getTitle().equals("Закрытый гостя rg_p2c")).findFirst().orElseThrow();
        assertThat(closed.getVisibility()).isEqualTo(TopicVisibility.CLOSED);

        // автор-гость может открыть свой закрытый тред
        mockMvc.perform(get("/forum/topic/" + closed.getId()).session(session))
                .andExpect(status().isOk());

        // гость создаёт «Открытый» (GUEST) тред
        mockMvc.perform(post("/forum/topic/create").session(session).with(csrf())
                        .param("title", "Открытый гостя rg_p2g")
                        .param("content", "Контент")
                        .param("visibility", "GUEST"))
                .andExpect(status().is3xxRedirection());
        ForumTopic guestTopic = topicRepository.findAll().stream()
                .filter(t -> t.getTitle().equals("Открытый гостя rg_p2g")).findFirst().orElseThrow();
        assertThat(guestTopic.getVisibility()).isEqualTo(TopicVisibility.GUEST);

        // гость НЕ может создать «Публичный» тред → становится GUEST
        mockMvc.perform(post("/forum/topic/create").session(session).with(csrf())
                        .param("title", "Публичный гость rg_p2p")
                        .param("content", "Контент")
                        .param("visibility", "PUBLIC"))
                .andExpect(status().is3xxRedirection());
        ForumTopic publicDowngraded = topicRepository.findAll().stream()
                .filter(t -> t.getTitle().equals("Публичный гость rg_p2p")).findFirst().orElseThrow();
        assertThat(publicDowngraded.getVisibility()).isNotEqualTo(TopicVisibility.PUBLIC);
    }

    @Test
    void guestCreateFormOffersGuestAndClosedChoicesOnly() throws Exception {
        createUser("auto_rg_guest_p2b", UserRole.GUEST);
        MockHttpSession session = login("auto_rg_guest_p2b");

        String form = mockMvc.perform(get("/forum/topic/create").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        // гость видит выбор «Открытый» (GUEST) и «Закрытый»
        assertThat(form).contains("value=\"GUEST\"", "value=\"CLOSED\"");
        // и не видит Публичный/Открытый-OPEN
        assertThat(form).doesNotContain("value=\"PUBLIC\"")
                .doesNotContain("value=\"OPEN\"");
    }

    @Test
    void residentCreateFormStillShowsOpenPublicClosed() throws Exception {
        createUser("auto_rg_resident_p2c", UserRole.RESIDENT);
        MockHttpSession session = login("auto_rg_resident_p2c");

        String form = mockMvc.perform(get("/forum/topic/create").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(form).contains("value=\"OPEN\"", "value=\"PUBLIC\"", "value=\"CLOSED\"");
    }

    @Test
    void guestCreatesOnlyGuestThreads() throws Exception {
        createUser("auto_rg_guest_3", UserRole.GUEST);
        MockHttpSession session = login("auto_rg_guest_3");

        mockMvc.perform(post("/forum/topic/create").session(session).with(csrf())
                        .param("title", "Гостевой тред rg_t3")
                        .param("content", "Контент")
                        .param("visibility", "OPEN"))
                .andExpect(status().is3xxRedirection());

        ForumTopic created = topicRepository.findAll().stream()
                .filter(t -> t.getTitle().equals("Гостевой тред rg_t3")).findFirst().orElseThrow();
        assertThat(created.getVisibility()).isEqualTo(TopicVisibility.GUEST);

        MockHttpSession session2 = login("auto_rg_guest_3");
        // гость, создавший гостевой тред, не видит маркер «доступен гостям» на странице треда
        mockMvc.perform(get("/forum/topic/" + created.getId()).session(session2))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("доступен гостям"))));
        assertThat(created.isReadOnly()).isFalse();
    }

    @Test
    void residentGuestAccessCheckboxResolvesToGuestVisibility() throws Exception {
        createUser("auto_rg_resident_ga1", UserRole.RESIDENT);
        MockHttpSession session = login("auto_rg_resident_ga1");

        mockMvc.perform(post("/forum/topic/create").session(session).with(csrf())
                        .param("title", "Открытый с гостями rg_ga1")
                        .param("content", "Контент")
                        .param("visibility", "OPEN")
                        .param("guestAccess", "true"))
                .andExpect(status().is3xxRedirection());

        ForumTopic guest = topicRepository.findAll().stream()
                .filter(t -> t.getTitle().equals("Открытый с гостями rg_ga1")).findFirst().orElseThrow();
        assertThat(guest.getVisibility()).isEqualTo(TopicVisibility.GUEST);

        mockMvc.perform(post("/forum/topic/create").session(session).with(csrf())
                        .param("title", "Открытый без гостей rg_ga2")
                        .param("content", "Контент")
                        .param("visibility", "OPEN"))
                .andExpect(status().is3xxRedirection());

        ForumTopic open = topicRepository.findAll().stream()
                .filter(t -> t.getTitle().equals("Открытый без гостей rg_ga2")).findFirst().orElseThrow();
        assertThat(open.getVisibility()).isEqualTo(TopicVisibility.OPEN);
    }

    @Test
    void guestAccessCheckboxPresentOnlyForResidentAndMasters() throws Exception {
        createUser("auto_rg_resident_ga3", UserRole.RESIDENT);
        MockHttpSession resSession = login("auto_rg_resident_ga3");
        mockMvc.perform(get("/forum/topic/create").session(resSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("guestAccess")))
                .andExpect(content().string(containsString("Доступен гостям")))
                // x-data должен быть объектным литералом {vis: '...'}, иначе Alpine не покажет чекбокс
                .andExpect(content().string(containsString("x-data=\"{vis: &#39;")));

        createUser("auto_rg_guest_ga4", UserRole.GUEST);
        MockHttpSession guestSession = login("auto_rg_guest_ga4");
        mockMvc.perform(get("/forum/topic/create").session(guestSession))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("guestAccess"))));
    }

    @Test
    void guestDefaultVisibilityPrefillsCheckboxInCreateForm() throws Exception {
        createUser("auto_rg_resident_ga5", UserRole.RESIDENT);
        MockHttpSession session = login("auto_rg_resident_ga5");

        mockMvc.perform(post("/settings/prefs").session(session).with(csrf())
                        .param("defaultVisibility", "GUEST"))
                .andExpect(status().is3xxRedirection());

        String page = mockMvc.perform(get("/forum/topic/create").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)
                .replaceAll("\\s+", " ");
        assertThat(page).contains("id=\"guestAccess\" name=\"guestAccess\" value=\"true\" checked=\"checked\"");
    }

    @Test
    void guestCannotFreezeResidentGuestThread() throws Exception {
        User resident = createUser("auto_rg_resident_4", UserRole.RESIDENT);
        createTopic(resident, "Гостевой на модерацию rg_g4", TopicVisibility.GUEST);
        createUser("auto_rg_guest_4", UserRole.GUEST);
        MockHttpSession session = login("auto_rg_guest_4");
        Long tid = topicIdByTitle("Гостевой на модерацию rg_g4");

        // гость может модерировать только свои треды
        mockMvc.perform(post("/forum/topic/" + tid + "/moderation/freeze")
                        .session(session).with(csrf()))
                .andExpect(status().is4xxClientError());

        assertThat(topicRepository.findById(tid).orElseThrow().isFrozen()).isFalse();
    }

    @Test
    void masterCanModerateAnyTopic() throws Exception {
        User resident = createUser("auto_rg_resident_4b", UserRole.RESIDENT);
        createTopic(resident, "Чужой тред для мастера rg_g4b", TopicVisibility.OPEN);
        User master = createUser("auto_rg_master_4b", UserRole.MASTER);
        MockHttpSession session = login("auto_rg_master_4b");
        Long tid = topicIdByTitle("Чужой тред для мастера rg_g4b");

        mockMvc.perform(post("/forum/topic/" + tid + "/moderation/freeze")
                        .session(session).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(topicRepository.findById(tid).orElseThrow().isFrozen()).isTrue();
    }

    @Test
    void readOnlyDefaultSettingPrefillsCreateForm() throws Exception {
        createUser("auto_rg_user_4c", UserRole.RESIDENT);
        MockHttpSession session = login("auto_rg_user_4c");

        mockMvc.perform(post("/settings/prefs").session(session).with(csrf())
                        .param("defaultReadOnly", "true"))
                .andExpect(status().is3xxRedirection());

        String page = mockMvc.perform(get("/forum/topic/create").session(session))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)
                .replaceAll("\\s+", " ");
        assertThat(page).contains("id=\"readOnly\" name=\"readOnly\" value=\"true\" checked=\"checked\"");
    }

    @Test
    void anonymousOpensPublicTopicWithoutError() throws Exception {
        User resident = createUser("auto_rg_resident_anon", UserRole.RESIDENT);
        createTopic(resident, "Публичный для анонима rg_anon1", TopicVisibility.PUBLIC);
        Long tid = topicIdByTitle("Публичный для анонима rg_anon1");

        mockMvc.perform(get("/forum/topic/" + tid))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Публичный для анонима rg_anon1")));
    }

    @Test
    void anonymousCreateFormRedirectsToLoginWithoutError() throws Exception {
        // прямой переход анонима на форму создания не даёт 500
        // (SettingsService.getByUserId(null) → findById(null))
        mockMvc.perform(get("/forum/topic/create"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl())
                        .contains("/login"));
    }

    @Test
    void residentSeesGuestMarkerOnGuestThread() throws Exception {
        User resident = createUser("auto_rg_resident_mk1", UserRole.RESIDENT);
        createTopic(resident, "Гостевой с маркером rg_mk1", TopicVisibility.GUEST);
        MockHttpSession session = login("auto_rg_resident_mk1");

        mockMvc.perform(get("/forum/topic/" + topicIdByTitle("Гостевой с маркером rg_mk1")).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Гостевой")));
    }

    @Test
    void cinemaAndFilmDataHiddenFromGuest() throws Exception {
        createUser("auto_rg_guest_5", UserRole.GUEST);
        MockHttpSession session = login("auto_rg_guest_5");

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
        User master = createUser("auto_rg_master_6", UserRole.MASTER);
        User guest = createUser("auto_rg_guest_6", UserRole.GUEST);
        MockHttpSession session = login("auto_rg_master_6");

        mockMvc.perform(post("/users/" + guest.getId() + "/promote").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(userRepository.findById(guest.getId()).orElseThrow().getRole())
                .isEqualTo(UserRole.RESIDENT);

        // повышенный резидент получает доступ к кинотеатру
        mockMvc.perform(get("/cinema").session(login("auto_rg_guest_6")))
                .andExpect(status().isOk());
    }

    @Test
    void keysPageScopedByRole() throws Exception {
        createUser("auto_rg_resident_7", UserRole.RESIDENT);
        MockHttpSession resSession = login("auto_rg_resident_7");

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
                        .param("username", "auto_rg_newguest_7")
                        .param("password", PASSWORD))
                .andExpect(status().is3xxRedirection());

        User newbie = userRepository.findByUsername("auto_rg_newguest_7").orElseThrow();
        assertThat(newbie.getRole()).isEqualTo(UserRole.GUEST);

        // гость не имеет доступа к странице ключей
        MockHttpSession guestSession = login("auto_rg_newguest_7");
        mockMvc.perform(get("/keys").session(guestSession))
                .andExpect(status().isForbidden());

        // мастер видит все ключи, резидент — только свои
        User master = createUser("auto_rg_master_7", UserRole.MASTER);
        MockHttpSession masterSession = login("auto_rg_master_7");
        mockMvc.perform(post("/keys/generate").session(masterSession).with(csrf())) // мастерский (не гостевой)
                .andExpect(status().isOk());

        String masterKeys = mockMvc.perform(get("/keys").session(masterSession))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(masterKeys).contains("auto_rg_resident_7"); // создатель гостевого ключа виден мастеру

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
        User guestOwner = createUser("auto_rg_guest_owner_16", UserRole.GUEST);
        createTopic(guestOwner, "Гостевой тред другого гостя rg_g16", TopicVisibility.GUEST);
        createUser("auto_rg_guest_16", UserRole.GUEST);
        MockHttpSession session = login("auto_rg_guest_16");
        Long tid = topicIdByTitle("Гостевой тред другого гостя rg_g16");

        mockMvc.perform(post("/forum/topic/" + tid + "/moderation/freeze")
                        .session(session).with(csrf()))
                .andExpect(status().is4xxClientError());

        assertThat(topicRepository.findById(tid).orElseThrow().isFrozen()).isFalse();
    }

    @Test
    void cinemaSectionHiddenOnGuestProfiles() throws Exception {
        User resident = createUser("auto_rg_resident_17", UserRole.RESIDENT);
        User guest = createUser("auto_rg_guest_17", UserRole.GUEST);

        MockHttpSession resSession = login("auto_rg_resident_17");
        mockMvc.perform(get("/profile/" + guest.getId()).session(resSession))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Кинотеатр"))))
                .andExpect(content().string(not(containsString("Средняя оценка вами его фильмов"))))
                .andExpect(content().string(not(containsString("Средняя оценка им ваших фильмов"))));

        MockHttpSession guestSession = login("auto_rg_guest_17");
        mockMvc.perform(get("/profile/" + resident.getId()).session(guestSession))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Кинотеатр"))));
    }
}
