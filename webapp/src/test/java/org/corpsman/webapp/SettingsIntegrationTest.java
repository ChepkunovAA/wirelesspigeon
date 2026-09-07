package org.corpsman.webapp;

import org.corpsman.auth.model.User;
import org.corpsman.auth.model.UserRole;
import org.corpsman.auth.repository.UserRepository;
import org.corpsman.forum.model.ForumPost;
import org.corpsman.forum.model.ForumTopic;
import org.corpsman.forum.model.TopicVisibility;
import org.corpsman.forum.repository.ForumPostRepository;
import org.corpsman.forum.repository.ForumTopicRepository;
import org.corpsman.forum.repository.TopicLikeRepository;
import org.corpsman.forum.model.TopicLike;
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
class SettingsIntegrationTest {

    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ForumTopicRepository topicRepository;

    @Autowired
    private TopicLikeRepository topicLikeRepository;

    @Autowired
    private ForumPostRepository postRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void ownProfileShowsSettingsLinkAndRating() throws Exception {
        User u = createUser("set_user_1");
        MockHttpSession session = login("set_user_1");

        mockMvc.perform(get("/profile").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/settings")))
                .andExpect(content().string(containsString("Совокупный рейтинг")));
    }

    @Test
    void settingsPageRendersAllControls() throws Exception {
        createUser("set_user_2");
        MockHttpSession session = login("set_user_2");

        String page = mockMvc.perform(get("/settings").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(page)
                .contains("defaultThreadAnonymous")
                .contains("defaultCommentAnonymous")
                .contains("defaultVisibility")
                .contains("showPublicPosts")
                .contains("oldPassword")
                .contains("newPassword");
    }

    @Test
    void hidingPublicPostsRemovesThemFromForumList() throws Exception {
        User author = createUser("set_author_3");
        User reader = createUser("set_reader_3");
        ForumTopic publicTopic = createPublicTopic(author, "Секретный публичный тред set_pub_3");
        MockHttpSession session = login("set_reader_3");

        mockMvc.perform(get("/forum").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Секретный публичный тред set_pub_3")));

        mockMvc.perform(post("/settings/prefs").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/forum").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Секретный публичный тред set_pub_3"))));

        assertThat(publicTopic.getVisibility()).isEqualTo(org.corpsman.forum.model.TopicVisibility.PUBLIC);
    }

    @Test
    void createFormPrefilledWithDefaultAnonymousAndPublic() throws Exception {
        createUser("set_user_4");
        MockHttpSession session = login("set_user_4");

        mockMvc.perform(get("/forum/topic/create").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("checked"))));

        mockMvc.perform(post("/settings/prefs").session(session).with(csrf())
                        .param("defaultThreadAnonymous", "true")
                        .param("defaultVisibility", "PUBLIC"))
                .andExpect(status().is3xxRedirection());

        String page = mockMvc.perform(get("/forum/topic/create").session(session))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(page).contains("checked");
    }

    @Test
    void changePasswordMakesOldInvalidAndNewValid() throws Exception {
        createUser("set_user_5");
        MockHttpSession session = login("set_user_5");

        mockMvc.perform(post("/settings/password").session(session).with(csrf())
                        .param("oldPassword", PASSWORD)
                        .param("newPassword", "newpass123"))
                .andExpect(status().is3xxRedirection());

        MvcResult oldLogin = mockMvc.perform(post("/login").with(csrf())
                        .param("username", "set_user_5")
                        .param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"))
                .andReturn();

        mockMvc.perform(post("/login").with(csrf())
                        .param("username", "set_user_5")
                        .param("password", "newpass123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void foreignProfileShowsGivenAndReceivedRating() throws Exception {
        User chief = createUser("set_chief_6");
        User reader = createUser("set_reader_6");
        ForumTopic topic = createPublicTopic(chief, "Тред для реакций set_rate_6");
        topicLikeRepository.save(topicLike(topic.getId(), reader.getId()));
        MockHttpSession session = login("set_reader_6");

        mockMvc.perform(get("/profile/" + chief.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Поставили ему все пользователи")))
                .andExpect(content().string(containsString("Поставил он всем пользователям")))
                .andExpect(content().string(containsString("Он поставил вам")))
                .andExpect(content().string(containsString("Вы поставили ему")));
    }

    @Test
    void hidingPublicPostsKeepsOwnPublicThreadInForumList() throws Exception {
        User author = createUser("set_author_11");
        ForumTopic myPublic = createPublicTopic(author, "Мой публичный тред set_pub_11");
        MockHttpSession session = login("set_author_11");

        mockMvc.perform(post("/settings/prefs").session(session).with(csrf())
                        .param("showPublicPosts", "false"))
                .andExpect(status().is3xxRedirection());

        String page = mockMvc.perform(get("/forum").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(page).contains("Мой публичный тред set_pub_11");
        assertThat(myPublic.getVisibility()).isEqualTo(org.corpsman.forum.model.TopicVisibility.PUBLIC);
    }

    private ForumTopic createPublicTopic(User author, String title) {
        ForumTopic topic = new ForumTopic();
        topic.setTitle(title);
        topic.setContent("Контент треда");
        topic.setAuthorId(author.getId());
        topic.setLastActivityAt(LocalDateTime.now());
        topic.setVisibility(TopicVisibility.PUBLIC);
        return topicRepository.save(topic);
    }

    private TopicLike topicLike(Long topicId, Long userId) {
        TopicLike like = new TopicLike();
        like.setTopicId(topicId);
        like.setUserId(userId);
        return like;
    }

    private User createUser(String username, UserRole role) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(role);
        user.setActive(true);
        return userRepository.save(user);
    }

    private User createUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(UserRole.RESIDENT);
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
    void forumListShowsTopicRatingAggregatedAcrossPosts() throws Exception {
        User author = createUser("set_author_7");
        ForumTopic topic = createPublicTopic(author, "Тред с реакциями set_rate_7");
        // лайки OP хранятся на треде
        topic.setLikes(50);
        topic.setDislikes(10);
        topicRepository.save(topic);
        ForumPost continuation = new ForumPost();
        continuation.setTopicId(topic.getId());
        continuation.setAuthorId(author.getId());
        continuation.setContent("Продолжение");
        continuation.setAnonymous(false);
        continuation.setLikes(27);
        continuation.setDislikes(3);
        continuation.setContinuation(true);
        postRepository.save(continuation);
        MockHttpSession session = login("set_author_7");

        String page = mockMvc.perform(get("/forum").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(page).contains(">77<").contains(">13<");
    }

    @Test
    void adminCanResetForeignPasswordWithGeneratedValue() throws Exception {
        User chief = createUser("set_chief_9", UserRole.MASTER);
        User victim = createUser("set_victim_9");
        MockHttpSession session = login("set_chief_9");

        mockMvc.perform(post("/users/" + victim.getId() + "/reset-password").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection());

        java.util.regex.Matcher m = java.util.regex.Pattern.compile("Новый пароль: ([A-Za-z0-9]{5})")
                .matcher(mockMvc.perform(get("/users").session(session))
                        .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        org.assertj.core.api.Assertions.assertThat(m.find()).isTrue();
        String generated = m.group(1);

        mockMvc.perform(post("/login").with(csrf())
                        .param("username", "set_victim_9")
                        .param("password", generated))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void foreignProfileUsesRenamedLabelsWithTooltipsAndDivider() throws Exception {
        User chief = createUser("set_chief_11", UserRole.MASTER);
        User reader = createUser("set_reader_11");
        ForumTopic topic = createPublicTopic(chief, "Тред подписей set_lbl_11");
        topicLikeRepository.save(topicLike(topic.getId(), reader.getId()));
        MockHttpSession session = login("set_reader_11");

        String page = mockMvc.perform(get("/profile/" + chief.getId()).session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(page)
                .contains(">Общий рейтинг</dt>")
                .contains(">Пользовательская оценка</dt>")
                .contains(">Ответная оценка</dt>")
                .contains(">Личная оценка</dt>")
                .contains("title=\"Поставили ему все пользователи\"")
                .contains("title=\"Поставил он всем пользователям\"")
                .contains("title=\"Он поставил вам\"")
                .contains("title=\"Вы поставили ему\"");
        assertThat(page.indexOf("Ответная оценка") > page.indexOf("Пользовательская оценка")).isTrue();
    }

    @Test
    void resetPasswordRequiresAdminRole() throws Exception {
        User regular = createUser("set_user_10");
        User target = createUser("set_target_10");
        MockHttpSession session = login("set_user_10");

        mockMvc.perform(post("/users/" + target.getId() + "/reset-password").session(session).with(csrf())
                        .param("newPassword", "freshpass1"))
                .andExpect(status().isForbidden());
    }
}
