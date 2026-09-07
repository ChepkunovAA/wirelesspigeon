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
import org.springframework.transaction.annotation.Transactional;

import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * П.1 релиза 2.4.0: переработка системы маркеров.
 * МОБ-бейдж «Открытый» убран из списка и страницы треда (OPEN/GUEST больше не
 * несут этот маркер), табы списка (Все/Публичные/Открытые/Закрытые) видны только
 * авторизованным; аноним видит только публичный список без табов. Маркер
 * «доступен гостям» для RESIDENT/MASTER сохраняется.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MarkerAndTabsIntegrationTest {

    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ForumTopicRepository topicRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User createUser(String username, UserRole role) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(role);
        user.setActive(true);
        return userRepository.save(user);
    }

    private ForumTopic createTopic(User author, String title, TopicVisibility visibility) {
        ForumTopic topic = new ForumTopic();
        topic.setTitle(title);
        topic.setContent("Содержание: " + title);
        topic.setAuthorId(author.getId());
        topic.setVisibility(visibility);
        return topicRepository.save(topic);
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
    void anonymousForumListHasNoTabsAndNoOpenBadge() throws Exception {
        User resident = createUser("mt_resident_1", UserRole.RESIDENT);
        createTopic(resident, "Публичный mt_a1", TopicVisibility.PUBLIC);
        createTopic(resident, "Открытый mt_a2", TopicVisibility.OPEN);
        createTopic(resident, "Закрытый mt_a3", TopicVisibility.CLOSED);

        String page = mockMvc.perform(get("/forum"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        // аноним видит только публичный список, без фильтров-табов
        assertThat(page).contains("Публичный mt_a1").doesNotContain("Открытый mt_a2")
                .doesNotContain("Закрытый mt_a3");
        assertThat(page).doesNotContain("type=OPEN").doesNotContain("type=PUBLIC")
                .doesNotContain("type=CLOSED").doesNotContain(">Все</a>");
        // маркер «Открытый» нигде не показывается
        assertThat(page).doesNotContain(">Открытый</span>").doesNotContain("bi-unlock");
    }

    @Test
    void residentForumListKeepsAllTabsAndNoOpenBadge() throws Exception {
        User resident = createUser("mt_resident_2", UserRole.RESIDENT);
        createTopic(resident, "Открытый mt_b1", TopicVisibility.OPEN);
        MockHttpSession session = login("mt_resident_2");

        String page = mockMvc.perform(get("/forum").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        // табы целы (включая «Открытые»)
        assertThat(page).contains("type=OPEN", "type=PUBLIC", "type=CLOSED", ">Все</a>");
        // тред виден, но маркер «Открытый» отсутствует
        assertThat(page).contains("Открытый mt_b1")
                .doesNotContain(">Открытый</span>").doesNotContain("bi-unlock");
    }

    @Test
    void openTopicDetailHasNoOpenBadgeGuestTopicKeepsGuestMarker() throws Exception {
        User resident = createUser("mt_resident_3", UserRole.RESIDENT);
        ForumTopic open = createTopic(resident, "Открытый mt_d1", TopicVisibility.OPEN);
        ForumTopic guest = createTopic(resident, "Гостевой mt_d2", TopicVisibility.GUEST);
        MockHttpSession session = login("mt_resident_3");

        String openPage = mockMvc.perform(get("/forum/topic/" + open.getId()).session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(openPage).contains("Открытый mt_d1")
                .doesNotContain(">Открытый</span>").doesNotContain("bi-unlock");

        String guestPage = mockMvc.perform(get("/forum/topic/" + guest.getId()).session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(guestPage).contains("Гостевой")
                .doesNotContain(">Открытый</span>").doesNotContain("bi-unlock");
    }
}