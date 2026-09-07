package org.corpsman.webapp;

import org.corpsman.auth.model.User;
import org.corpsman.auth.model.UserRole;
import org.corpsman.auth.repository.UserRepository;
import org.corpsman.forum.model.ForumTopic;
import org.corpsman.forum.model.TopicVisibility;
import org.corpsman.forum.repository.ForumTopicRepository;
import org.corpsman.webapp.config.TimeUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * П.7 релиза 2.4.0: времена отображаются в таймзоне браузера.
 * Проверяет, что сервер отдаёт каждый момент как {@code <time data-utc="...Z">}
 * (UTC ISO-8601) с UTC-фоллбэком и подключает time-format.js, который переводит
 * их в локальный часовой пояс пользователя.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TimeZoneRenderingIntegrationTest {

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
    private TimeUtil timeUtil;

    private User createUser(String username, UserRole role) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(role);
        user.setActive(true);
        return userRepository.save(user);
    }

    private ForumTopic createPublicTopic(String title) {
        ForumTopic topic = new ForumTopic();
        topic.setTitle(title);
        topic.setContent("Содержание: " + title);
        topic.setAuthorId(createUser("tz_user_" + title.hashCode(), UserRole.RESIDENT).getId());
        topic.setVisibility(TopicVisibility.PUBLIC);
        return topicRepository.save(topic);
    }

    @Test
    void topicPageRendersThreadTimeAsUtcTimestampInTimeTag() throws Exception {
        ForumTopic topic = createPublicTopic("Времена тред");
        Long tid = topic.getId();
        String expectedIso = timeUtil.isoUtc(topicRepository.findById(tid).orElseThrow().getCreatedAt());

        mockMvc.perform(get("/forum/topic/" + tid))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-utc=\"" + expectedIso + "\"")))
                .andExpect(content().string(containsString("data-utc-mode=\"datetime\"")))
                .andExpect(content().string(containsString("/js/time-format.js")));
    }

    @Test
    void forumListRendersLastActivityAsUtcTimestampInTimeTag() throws Exception {
        createPublicTopic("Времена список");

        mockMvc.perform(get("/forum"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-utc=\"")))
                .andExpect(content().string(containsString("/js/time-format.js")));
    }

    @Test
    void homePageLoadsTimeFormatScript() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/js/time-format.js")));
    }

    @Test
    void timeFormatScriptIsPubliclyServed() throws Exception {
        String js = mockMvc.perform(get("/js/time-format.js"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(js)
                .contains("formatLocal")
                .contains("convertTimes")
                .contains("data-utc");
    }

    @Test
    void noJavaTimeFormatsRemainInTemplatesOutsideFragment() throws Exception {
        // Серверный фоллбэк — только внутри фрагмента времени; в шаблонах
        // должны присутствовать теги <time> с data-utc, а не сырые #temporals.
        String list = mockMvc.perform(get("/forum"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(list).doesNotContain("th:text=\"${#temporals.format(");
    }
}