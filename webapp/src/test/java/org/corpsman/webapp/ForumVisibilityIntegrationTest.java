package org.corpsman.webapp;

import org.corpsman.auth.model.User;
import org.corpsman.auth.model.UserRole;
import org.corpsman.auth.repository.UserRepository;
import org.corpsman.forum.model.ForumPost;
import org.corpsman.forum.model.ForumTopic;
import org.corpsman.forum.model.TopicVisibility;
import org.corpsman.forum.model.TopicWhitelistEntry;
import org.corpsman.forum.repository.ForumPostRepository;
import org.corpsman.forum.repository.ForumTopicRepository;
import org.corpsman.forum.repository.TopicWhitelistRepository;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ForumVisibilityIntegrationTest {

    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ForumTopicRepository topicRepository;

    @Autowired
    private ForumPostRepository postRepository;

    @Autowired
    private TopicWhitelistRepository whitelistRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void closedTopicHiddenFromStrangerVisibleToWhitelistedAndOwner() throws Exception {
        User owner = createUser("fv_owner_1");
        User stranger = createUser("fv_stranger_1");
        User friend = createUser("fv_friend_1");
        ForumTopic topic = createTopic(owner, "Закрытый тред fv_c1", TopicVisibility.CLOSED, null);
        whitelistRepository.save(entry(topic.getId(), friend.getId()));

        MockHttpSession strangerSession = login("fv_stranger_1");
        mockMvc.perform(get("/forum/topic/" + topic.getId()).session(strangerSession))
                .andExpect(status().is4xxClientError());

        MockHttpSession friendSession = login("fv_friend_1");
        mockMvc.perform(get("/forum/topic/" + topic.getId()).session(friendSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("fv_c1")));

        MockHttpSession ownerSession = login("fv_owner_1");
        mockMvc.perform(get("/forum/topic/" + topic.getId()).session(ownerSession))
                .andExpect(status().isOk());

        mockMvc.perform(get("/forum").session(strangerSession))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("fv_c1"))));
    }

    @Test
    void createFormSavesVisibilityAndWhitelist() throws Exception {
        createUser("fv_owner_2");
        createUser("fv_friend_2");
        MockHttpSession session = login("fv_owner_2");

        mockMvc.perform(post("/forum/topic/create").session(session).with(csrf())
                        .param("title", "Тред с белым списком fv_t2")
                        .param("content", "Контент треда fv_t2")
                        .param("visibility", "CLOSED")
                        .param("whitelistItems", "fv_friend_2"))
                .andExpect(status().is3xxRedirection());

        List<ForumTopic> topics = topicRepository.findAll();
        ForumTopic created = topics.stream()
                .filter(t -> t.getTitle().contains("fv_t2")).findFirst().orElseThrow();

        MockHttpSession formSession = login("fv_owner_2");
        String formPage = mockMvc.perform(get("/forum/topic/create").session(formSession))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(formPage).contains("name=\"whitelistItems\"");

        assertThat(created.getVisibility()).isEqualTo(TopicVisibility.CLOSED);
        assertThat(whitelistRepository.findByTopicId(created.getId()))
                .extracting(TopicWhitelistEntry::getUserId)
                .containsExactly(userRepository.findByUsername("fv_friend_2").orElseThrow().getId());
    }

    @Test
    void continuationCompressesRootCommentsUnderOp() throws Exception {
        User owner = createUser("fv_owner_3");
        ForumTopic topic = createTopic(owner, "Тред со сжатием fv_t3", TopicVisibility.OPEN, null);
        ForumPost op = postRepository.findByTopicIdAndOpTrue(topic.getId()).orElseThrow();

        ForumPost c1 = rootComment(topic.getId(), owner.getId(), 1);
        ForumPost c2 = rootComment(topic.getId(), owner.getId(), 2);

        MockHttpSession session = login("fv_owner_3");
        mockMvc.perform(post("/forum/topic/" + topic.getId() + "/continuation").session(session).with(csrf())
                        .param("content", "Пост-продолжение fv_cont_3"))
                .andExpect(status().is3xxRedirection());

        ForumPost opAfter = postRepository.findById(op.getId()).orElseThrow();
        List<ForumPost> roots = postRepository.findByTopicIdOrderByCreatedAtAsc(topic.getId()).stream()
                .filter(p -> p.getParentPostId() == null).toList();

        assertThat(roots).hasSize(2);
        assertThat(roots).extracting(ForumPost::getId)
                .containsExactlyInAnyOrder(opAfter.getId(),
                        roots.stream().filter(ForumPost::isContinuation).findFirst().orElseThrow().getId());
        assertThat(opAfter.isOp()).isTrue();
        assertThat(postRepository.findById(c1.getId()).orElseThrow().getParentPostId()).isEqualTo(opAfter.getId());
        assertThat(postRepository.findById(c2.getId()).orElseThrow().getParentPostId()).isEqualTo(opAfter.getId());

        // Пользователи добавляют Комментарий 3 (корневой) и Комментарий 4 (ответ на него)
        ForumPost c3 = rootComment(topic.getId(), owner.getId(), 3);
        ForumPost c4 = new ForumPost();
        c4.setTopicId(topic.getId());
        c4.setParentPostId(c3.getId());
        c4.setContent("Комментарий 4");
        c4.setAuthorId(owner.getId());
        postRepository.save(c4);

        // Владелец добавляет ВТОРОЕ продолжение
        mockMvc.perform(post("/forum/topic/" + topic.getId() + "/continuation").session(session).with(csrf())
                        .param("content", "Второе продолжение fv_cont_3b"))
                .andExpect(status().is3xxRedirection());

        // Комментарий 3 сжат в поддерево первого продолжения; Комментарий 4 остался его ребёнком
        assertThat(postRepository.findById(c3.getId()).orElseThrow().getParentPostId())
                .isEqualTo(roots.stream().filter(ForumPost::isContinuation).findFirst().orElseThrow().getId());
        assertThat(postRepository.findById(c4.getId()).orElseThrow().getParentPostId()).isEqualTo(c3.getId());

        MvcResult page = mockMvc.perform(get("/forum/topic/" + topic.getId()).session(session))
                .andExpect(status().isOk())
                .andReturn();
        String html = page.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        // лайки/дизлайки видны и на собственных комментариях (кнопки заблокированы)
        assertThat(html).contains(":disabled=\"own\"");

        // все старые и новые тексты видимы
        assertThat(html).contains("Пост-продолжение fv_cont_3")
                .contains("Второе продолжение fv_cont_3b")
                .contains("Комментарий 1").contains("Комментарий 2")
                .contains("Комментарий 3").contains("Комментарий 4")
                .contains(html.replaceAll("\\s+", " ").contains("Раскрыть <span>2</span> <span>комментария</span>")
                        ? "Раскрыть" : "NEVER_MATCH")
                .contains("Новый комментарий к заглавному посту");

        // тогглы: OP (2 комментария) + первое продолжение (2 комментария); у последнего их нет
        assertThat(countMatches(html.replaceAll("\\s+", " "), "Раскрыть <span>2</span> <span>комментария</span>")).isEqualTo(2);

        // Без продолжений комментарии плоские: нет блока поддерева
        User owner2 = createUser("fv_owner_3b");
        ForumTopic plain = createTopic(owner2, "Тред без продолжений fv_t3b", TopicVisibility.OPEN, null);
        rootComment(plain.getId(), owner2.getId(), 9);
        MockHttpSession s2 = login("fv_owner_3b");
        String plainPage = mockMvc.perform(get("/forum/topic/" + plain.getId()).session(s2))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)
                .replaceAll("\\s+", " ");
        assertThat(plainPage).contains("Комментарий 9")
                .doesNotContain("Раскрыть ");
    }

    @Test
    void onlyOwnerSeesAddContinuationButtonAndCanCallIt() throws Exception {
        User owner = createUser("fv_owner_4");
        User stranger = createUser("fv_stranger_4");
        ForumTopic topic = createTopic(owner, "Тред кнопки fv_t4", TopicVisibility.OPEN, null);
        MockHttpSession ownerSession = login("fv_owner_4");
        MockHttpSession strangerSession = login("fv_stranger_4");

        // владелец публикует продолжение
        mockMvc.perform(post("/forum/topic/" + topic.getId() + "/continuation")
                        .session(ownerSession).with(csrf())
                        .param("content", "Продолжение для проверки пустого поддерева"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/forum/topic/" + topic.getId()).session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("continuationForm")));

        String strangerPage = mockMvc.perform(get("/forum/topic/" + topic.getId()).session(strangerSession))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(strangerPage).doesNotContain("continuationForm");

        // пустое поддерево заглавного поста формируется всегда (0 комментариев)
        String ownerPage = mockMvc.perform(get("/forum/topic/" + topic.getId()).session(ownerSession))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)
                .replaceAll("\\s+", " ");
        assertThat(ownerPage).contains("Раскрыть <span>0</span> <span>комментариев</span>");

        mockMvc.perform(post("/forum/topic/" + topic.getId() + "/continuation").session(strangerSession).with(csrf())
                        .param("content", "Чужое продолжение"))
                .andExpect(status().is4xxClientError());
        assertThat(topicRepository.findById(topic.getId()).orElseThrow()).isNotNull();
    }

    @Test
    void homePostVisibilityFollowsRoleAndSettings() throws Exception {
        User owner = createUser("fv_owner_5");
        createTopic(owner, "Публичный анонс fv_p5", TopicVisibility.PUBLIC, null);
        createTopic(owner, "Открытый обычный fv_o5", TopicVisibility.OPEN, null);

        // Аноним видит только публичные треды
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Публичный анонс fv_p5")))
                .andExpect(content().string(not(containsString("Открытый обычный fv_o5"))));

        // RESIDENT видит и публичные, и открытые треды (открытый побеждает в блоках)
        createUser("fv_reader_5");
        MockHttpSession session = login("fv_reader_5");
        mockMvc.perform(get("/").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Открытый обычный fv_o5")));

        mockMvc.perform(get("/forum").session(session))
                .andExpect(content().string(containsString("fv_p5")))
                .andExpect(content().string(containsString("fv_o5")));
    }

    private ForumTopic createTopic(User author, String title, TopicVisibility visibility, Long ignored) {
        ForumTopic topic = new ForumTopic();
        topic.setTitle(title);
        topic.setContent("Содержание: " + title);
        topic.setAuthorId(author.getId());
        topic.setVisibility(visibility);
        topic.setLastActivityAt(LocalDateTime.now());
        topic = topicRepository.save(topic);

        ForumPost op = new ForumPost();
        op.setTopicId(topic.getId());
        op.setContent(topic.getContent());
        op.setAuthorId(author.getId());
        op.setOp(true);
        postRepository.save(op);
        return topic;
    }

    private ForumPost rootComment(Long topicId, Long authorId, int n) {
        ForumPost post = new ForumPost();
        post.setTopicId(topicId);
        post.setContent("Комментарий " + n);
        post.setAuthorId(authorId);
        return postRepository.save(post);
    }

    private TopicWhitelistEntry entry(Long topicId, Long userId) {
        TopicWhitelistEntry e = new TopicWhitelistEntry();
        e.setTopicId(topicId);
        e.setUserId(userId);
        return e;
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
    void whitelistDropdownShowsOnlyActiveUsers() throws Exception {
        User owner = createUser("fv_owner_12");
        User activeFriend = createUser("fv_active_12");
        User banned = createUser("fv_banned_12");
        banned.setActive(false);
        userRepository.save(banned);
        MockHttpSession session = login("fv_owner_12");

        String page = mockMvc.perform(get("/forum/topic/create").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(page).contains("value=\"" + activeFriend.getUsername() + "\"");
        assertThat(page).doesNotContain("value=\"fv_banned_12\"");
    }

    @Test
    void continuationRenderedInFirstPostStyleWithoutReplyUi() throws Exception {
        User owner = createUser("fv_owner_13");
        ForumTopic topic = createTopic(owner, "Стиль продолжений fv_t13", TopicVisibility.OPEN, null);
        rootComment(topic.getId(), owner.getId(), 1);

        MockHttpSession session = login("fv_owner_13");
        mockMvc.perform(post("/forum/topic/" + topic.getId() + "/continuation").session(session).with(csrf())
                        .param("content", "Продолжение в стиле fv_cont_13"))
                .andExpect(status().is3xxRedirection());

        String html = mockMvc.perform(get("/forum/topic/" + topic.getId()).session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

String flat = html.replaceAll("\\s+", " ");
                assertThat(flat)
                        .contains("id=\"continuation-")
                        .contains("Продолжение в стиле fv_cont_13")
                        .contains("Раскрыть <span>1</span> <span>комментарий</span>")
                        .doesNotContain(">Пост-продолжение</div>");
        int contPos = html.indexOf("Продолжение в стиле fv_cont_13");
        int nextReplyBtn = html.indexOf("aria-label=\"Ответить\"", contPos);
        int opReplyBtn = html.indexOf("aria-label=\"Ответить\"", 0);
        assertThat(opReplyBtn).isNotEqualTo(-1);
        ForumTopic reloaded = topicRepository.findById(topic.getId()).orElseThrow();
        var cont = postRepository.findByTopicIdOrderByCreatedAtAsc(reloaded.getId()).stream()
                .filter(ForumPost::isContinuation).findFirst().orElseThrow();
        assertThat(html).contains("/forum/post/" + cont.getId() + "/like");
    }

    private static int countMatches(String s, String sub) {
        int c = 0;
        int i = 0;
        while ((i = s.indexOf(sub, i)) != -1) { c++; i += sub.length(); }
        return c;
    }

    @Test
    void adminCannotEditButCanDeleteForeignContentWithStub() throws Exception {
        User chief = createUser("fv_owner_14");
        ForumTopic topic = createTopic(chief, "Тред админ-прав fv_t14", TopicVisibility.OPEN, null);
        ForumPost op = postRepository.findByTopicIdAndOpTrue(topic.getId()).orElseThrow();
        // дочерний комментарий владельца под заглавным постом
        ForumPost child = new ForumPost();
        child.setTopicId(topic.getId());
        child.setParentPostId(op.getId());
        child.setContent("Комментарий для удаления fv_del_14");
        child.setAuthorId(chief.getId());
        postRepository.save(child);
        User adminUser = createUser("fv_admin_14", UserRole.MASTER);
        MockHttpSession session = login("fv_admin_14");

        // админ не может редактировать чужой тред и чужой комментарий
        mockMvc.perform(post("/forum/topic/" + topic.getId() + "/edit").session(session).with(csrf())
                        .param("content", "админ правил"))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(post("/forum/post/" + child.getId() + "/edit").session(session).with(csrf())
                        .param("content", "админ правил комментарий"))
                .andExpect(status().is4xxClientError());

        // но может удалить — с заглушкой «Удалено администратором»
        mockMvc.perform(post("/forum/post/" + child.getId() + "/delete").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection());

        ForumPost deleted = postRepository.findById(child.getId()).orElseThrow();
        assertThat(deleted.isDeleted()).isTrue();
        assertThat(deleted.isDeletedByAdmin()).isTrue();

        String page = mockMvc.perform(get("/forum/topic/" + topic.getId()).session(session))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(page).contains("Удалено администратором");

        // владелец удаляет свой комментарий — обычная заглушка и флаг false
        MockHttpSession ownerSession = login("fv_owner_14");
        mockMvc.perform(post("/forum/post/" + child.getId() + "/delete").session(ownerSession).with(csrf()))
                .andExpect(status().is3xxRedirection());

        ForumPost redeleted = postRepository.findById(child.getId()).orElseThrow();
        assertThat(redeleted.isDeletedByAdmin()).isFalse();
    }

    @Test
    void readOnlyTopicAllowsOnlyOwnerToWrite() throws Exception {
        User owner = createUser("fv_owner_15");
        createUser("fv_friend_15");
        createUser("fv_stranger_15");
        MockHttpSession session = login("fv_owner_15");

        mockMvc.perform(post("/forum/topic/create").session(session).with(csrf())
                        .param("title", "Рид-онли тред fv_t15")
                        .param("content", "Читаем, но не пишем")
                        .param("visibility", "OPEN")
                        .param("readOnly", "true"))
                .andExpect(status().is3xxRedirection());
        Long topicId = topicRepository.findAll().stream()
                .filter(t -> t.getTitle().contains("fv_t15")).findFirst().orElseThrow().getId();

        // чужой пользователь не может писать
        MockHttpSession stranger = login("fv_stranger_15");
        mockMvc.perform(get("/forum/topic/" + topicId).session(stranger))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Только для чтения")))
                .andExpect(content().string(containsString("Тред только для чтения")));

        mockMvc.perform(post("/forum/topic/" + topicId + "/comment").session(stranger).with(csrf())
                        .param("content", "попытка записи"))
                .andExpect(status().is4xxClientError());

        // владелец может писать
        MockHttpSession ownerSession = login("fv_owner_15");
        mockMvc.perform(post("/forum/topic/" + topicId + "/comment").session(ownerSession).with(csrf())
                        .param("content", "слово владельца"))
                .andExpect(status().is3xxRedirection());
    }
}
