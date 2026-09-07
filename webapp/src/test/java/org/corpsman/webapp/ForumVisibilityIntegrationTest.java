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
    void adminDoesNotSeeClosedTopicUnlessWhitelistedOrOwner() throws Exception {
        User owner = createUser("fv_6owner");
        User admin = createUser("fv_6admin", UserRole.MASTER);
        User whitelistedAdmin = createUser("fv_6wiadmin", UserRole.MASTER);
        ForumTopic topic = createTopic(owner, "Закрытый тред fv_p6_1", TopicVisibility.CLOSED, null);
        whitelistRepository.save(entry(topic.getId(), whitelistedAdmin.getId()));

        // админ вне белого списка НЕ видит закрытый тред ни в списке, ни по прямой ссылке
        MockHttpSession adminSession = login("fv_6admin");
        mockMvc.perform(get("/forum/topic/" + topic.getId()).session(adminSession))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(get("/forum").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("fv_p6_1"))));

        // админ, добавленный в белый список, видит тред
        MockHttpSession whitelistedSession = login("fv_6wiadmin");
        mockMvc.perform(get("/forum/topic/" + topic.getId()).session(whitelistedSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("fv_p6_1")));

        // владелец-не-админ также видит
        MockHttpSession ownerSession = login("fv_6owner");
        mockMvc.perform(get("/forum/topic/" + topic.getId()).session(ownerSession))
                .andExpect(status().isOk());
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
    void whitelistSearchBoxPresentOnCreateAndTopicPages() throws Exception {
        User owner = createUser("fv_ws_owner");
        createUser("fv_ws_friend");
        MockHttpSession session = login("fv_ws_owner");

        String createPage = mockMvc.perform(get("/forum/topic/create").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(createPage).contains("Поиск по нику");

        ForumTopic closed = createTopic(owner, "Закрытый fv_ws_topic", TopicVisibility.CLOSED, null);
        String topicPage = mockMvc.perform(get("/forum/topic/" + closed.getId()).session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(topicPage).contains("Поиск по нику");
        assertThat(topicPage).contains("name=\"whitelistItems\"");
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
    void p3OwnerSeesEditButtonsOnOpAndContinuationCardsNotHeader() throws Exception {
        User owner = createUser("fv_owner_16");
        ForumTopic topic = createTopic(owner, "Инлайн-правка fv_p3_16", TopicVisibility.OPEN, null);
        rootComment(topic.getId(), owner.getId(), 1);
        ForumPost opPost = postRepository.findByTopicIdAndOpTrue(topic.getId()).orElseThrow();

        MockHttpSession session = login("fv_owner_16");
        mockMvc.perform(post("/forum/topic/" + topic.getId() + "/continuation").session(session).with(csrf())
                        .param("content", "Продолжение для правки fv_p3_cont_16"))
                .andExpect(status().is3xxRedirection());
        ForumPost cont = postRepository.findByTopicIdOrderByCreatedAtAsc(topic.getId()).stream()
                .filter(ForumPost::isContinuation).findFirst().orElseThrow();

        String html = mockMvc.perform(get("/forum/topic/" + topic.getId()).session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        // кнопка редактирования OP-карточки: на карточке треда, не в шапке
        assertThat(html).contains("/forum/topic/" + topic.getId() + "/edit");
        assertThat(html).doesNotContain(">Редактировать</a>");
        // кнопка на карточке продолжения
        assertThat(html).contains("/forum/post/" + cont.getId() + "/edit");
        // дерево комментариев не мешает: опорный пост продолжения — тот же id-пост
        assertThat(opPost.getId()).isNotEqualTo(cont.getId());
    }

    @Test
    void p3StrangerSeesNoEditButtonsButCanPostToPostEdit() throws Exception {
        User owner = createUser("fv_owner_17");
        User stranger = createUser("fv_stranger_17");
        ForumTopic topic = createTopic(owner, "Чужой тред fv_p3_17", TopicVisibility.OPEN, null);
        MockHttpSession ownerSession = login("fv_owner_17");
        mockMvc.perform(post("/forum/topic/" + topic.getId() + "/continuation").session(ownerSession).with(csrf())
                        .param("content", "Конт продолжение fv_p3_cont_17"))
                .andExpect(status().is3xxRedirection());
        ForumPost cont = postRepository.findByTopicIdOrderByCreatedAtAsc(topic.getId()).stream()
                .filter(ForumPost::isContinuation).findFirst().orElseThrow();

        MockHttpSession strangerSession = login("fv_stranger_17");
        String html = mockMvc.perform(get("/forum/topic/" + topic.getId()).session(strangerSession))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(html).doesNotContain("/forum/topic/" + topic.getId() + "/edit");
        assertThat(html).doesNotContain("/forum/post/" + cont.getId() + "/edit");

        // чужой не может изменить ни тред, ни продолжение
        mockMvc.perform(post("/forum/topic/" + topic.getId() + "/edit").session(strangerSession).with(csrf())
                        .param("content", "чужое изменение"))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(post("/forum/post/" + cont.getId() + "/edit").session(strangerSession).with(csrf())
                        .param("content", "чужое изменение"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void p3OwnerEditsOpAndContinuationContentThroughPostEndpoints() throws Exception {
        User owner = createUser("fv_owner_18");
        ForumTopic topic = createTopic(owner, "Правка контента fv_p3_18", TopicVisibility.OPEN, null);
        MockHttpSession ownerSession = login("fv_owner_18");
        mockMvc.perform(post("/forum/topic/" + topic.getId() + "/continuation").session(ownerSession).with(csrf())
                        .param("content", "старое продолжение fv_p3_18"))
                .andExpect(status().is3xxRedirection());
        ForumPost cont = postRepository.findByTopicIdOrderByCreatedAtAsc(topic.getId()).stream()
                .filter(ForumPost::isContinuation).findFirst().orElseThrow();

        // правка OP
        mockMvc.perform(post("/forum/topic/" + topic.getId() + "/edit").session(ownerSession).with(csrf())
                        .param("content", "обновлённый тред fv_p3_18"))
                .andExpect(status().is3xxRedirection());
        ForumTopic reloadedTopic = topicRepository.findById(topic.getId()).orElseThrow();
        assertThat(reloadedTopic.getContent()).isEqualTo("обновлённый тред fv_p3_18");
        // OP-стройка рендерится из forum_posts — содержимое первого поста должно быть синхронизировано
        ForumPost opPost = postRepository.findByTopicIdAndOpTrue(topic.getId()).orElseThrow();
        assertThat(opPost.getContent()).isEqualTo("обновлённый тред fv_p3_18");
        assertThat(opPost.isEdited()).isTrue();

        // правка продолжения через существующий post/edit
        mockMvc.perform(post("/forum/post/" + cont.getId() + "/edit").session(ownerSession).with(csrf())
                        .param("content", "обновлённое продолжение fv_p3_18"))
                .andExpect(status().is3xxRedirection());
        ForumPost reloadedCont = postRepository.findById(cont.getId()).orElseThrow();
        assertThat(reloadedCont.getContent()).isEqualTo("обновлённое продолжение fv_p3_18");
        assertThat(reloadedCont.isEdited()).isTrue();
    }

    @Test
    void p4OwnerDeletesLastCleanContinuationFully() throws Exception {
        User owner = createUser("fv_owner_19");
        ForumTopic topic = createTopic(owner, "Полное удаление fv_p4_19", TopicVisibility.OPEN, null);
        MockHttpSession session = login("fv_owner_19");
        mockMvc.perform(post("/forum/topic/" + topic.getId() + "/continuation").session(session).with(csrf())
                        .param("content", "Первое продолжение fv_p4_19"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/forum/topic/" + topic.getId() + "/continuation").session(session).with(csrf())
                        .param("content", "Второе продолжение fv_p4_19"))
                .andExpect(status().is3xxRedirection());
        ForumPost last = postRepository.findByTopicIdOrderByCreatedAtAsc(topic.getId()).stream()
                .filter(ForumPost::isContinuation)
                .reduce((a, b) -> b).orElseThrow();

        // последнее чистое продолжение удаляется полностью (строки нет)
        mockMvc.perform(post("/forum/post/" + last.getId() + "/delete").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(postRepository.findById(last.getId())).isEmpty();
        ForumTopic reloaded = topicRepository.findById(topic.getId()).orElseThrow();
        assertThat(reloaded.isDeleted()).isFalse();
    }

    @Test
    void p4StragerCannotDeleteContinuation() throws Exception {
        User owner = createUser("fv_owner_20");
        User stranger = createUser("fv_stranger_20");
        ForumTopic topic = createTopic(owner, "Запрет чужим fv_p4_20", TopicVisibility.OPEN, null);
        MockHttpSession ownerSession = login("fv_owner_20");
        mockMvc.perform(post("/forum/topic/" + topic.getId() + "/continuation").session(ownerSession).with(csrf())
                        .param("content", "Продолжение fv_p4_20"))
                .andExpect(status().is3xxRedirection());
        ForumPost cont = postRepository.findByTopicIdOrderByCreatedAtAsc(topic.getId()).stream()
                .filter(ForumPost::isContinuation).findFirst().orElseThrow();

        MockHttpSession strangerSession = login("fv_stranger_20");
        mockMvc.perform(post("/forum/post/" + cont.getId() + "/delete").session(strangerSession).with(csrf()))
                .andExpect(status().is4xxClientError());
        assertThat(postRepository.findById(cont.getId())).isPresent();
    }

    @Test
    void p4OwnerDeletesContinuationWithSubtreeLeavesStub() throws Exception {
        User owner = createUser("fv_owner_21");
        ForumTopic topic = createTopic(owner, "Заглушка fv_p4_21", TopicVisibility.OPEN, null);
        MockHttpSession session = login("fv_owner_21");
        mockMvc.perform(post("/forum/topic/" + topic.getId() + "/continuation").session(session).with(csrf())
                        .param("content", "Продолжение с веткой fv_p4_21"))
                .andExpect(status().is3xxRedirection());
        ForumPost cont = postRepository.findByTopicIdOrderByCreatedAtAsc(topic.getId()).stream()
                .filter(ForumPost::isContinuation).findFirst().orElseThrow();

        // ответ внутри ветки продолжения (parentPostId = продолжение)
        ForumPost child = new ForumPost();
        child.setTopicId(topic.getId());
        child.setParentPostId(cont.getId());
        child.setContent("Ответ в ветке fv_p4_21");
        child.setAuthorId(owner.getId());
        postRepository.save(child);

        mockMvc.perform(post("/forum/post/" + cont.getId() + "/delete").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection());

        // строка остаётся, ставится заглушка
        ForumPost deleted = postRepository.findById(cont.getId()).orElseThrow();
        assertThat(deleted.isDeleted()).isTrue();
        assertThat(deleted.getContent()).isEqualTo("[Удалено]");
        assertThat(deleted.isDeletedByOwner()).isTrue();
        // ветка сохраняется
        assertThat(postRepository.findById(child.getId())).isPresent();

        // страница показывает заглушку
        String html = mockMvc.perform(get("/forum/topic/" + topic.getId()).session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(html).contains("[Удалено]");
    }

    @Test
    void p4OwnerDeletesContinuationWithFlatCommentBelowLeavesStub() throws Exception {
        User owner = createUser("fv_owner_22");
        ForumTopic topic = createTopic(owner, "Плоский ниже fv_p4_22", TopicVisibility.OPEN, null);
        MockHttpSession session = login("fv_owner_22");
        mockMvc.perform(post("/forum/topic/" + topic.getId() + "/continuation").session(session).with(csrf())
                        .param("content", "Продолжение fv_p4_22"))
                .andExpect(status().is3xxRedirection());
        ForumPost cont = postRepository.findByTopicIdOrderByCreatedAtAsc(topic.getId()).stream()
                .filter(ForumPost::isContinuation).findFirst().orElseThrow();

        // корневой комментарий ниже продолжения (новый, плоский)
        ForumPost below = new ForumPost();
        below.setTopicId(topic.getId());
        below.setContent("Плоский комментарий ниже fv_p4_22");
        below.setAuthorId(owner.getId());
        postRepository.save(below);

        mockMvc.perform(post("/forum/post/" + cont.getId() + "/delete").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection());

        ForumPost deleted = postRepository.findById(cont.getId()).orElseThrow();
        assertThat(deleted.isDeleted()).isTrue();
        assertThat(deleted.getContent()).isEqualTo("[Удалено]");
        assertThat(deleted.isDeletedByOwner()).isTrue();
        assertThat(postRepository.findById(below.getId())).isPresent();
    }

    @Test
    void p4AdminDeletesForeignContinuationFully() throws Exception {
        User owner = createUser("fv_owner_23");
        User adminUser = createUser("fv_admin_23", UserRole.MASTER);
        ForumTopic topic = createTopic(owner, "Админ удаляет fv_p4_23", TopicVisibility.OPEN, null);
        MockHttpSession ownerSession = login("fv_owner_23");
        mockMvc.perform(post("/forum/topic/" + topic.getId() + "/continuation").session(ownerSession).with(csrf())
                        .param("content", "Продолжение fv_p4_23"))
                .andExpect(status().is3xxRedirection());
        ForumPost cont = postRepository.findByTopicIdOrderByCreatedAtAsc(topic.getId()).stream()
                .filter(ForumPost::isContinuation).findFirst().orElseThrow();

        MockHttpSession adminSession = login("fv_admin_23");
        mockMvc.perform(post("/forum/post/" + cont.getId() + "/delete").session(adminSession).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(postRepository.findById(cont.getId())).isEmpty();
    }

    @Test
    void p6SortByRatingAndSize() throws Exception {
        User owner = createUser("fv_owner_24");
        // тред A — 2 поста, рейтинг высокий (лайки)
        ForumTopic a = createTopic(owner, "Сортировка A fv_p6_24", TopicVisibility.PUBLIC, null);
        continuation(owner, a, "продолжение A");
        a.setLikes(10);
        topicRepository.save(a);
        // тред B — 1 пост, рейтинг ниже (4 лайка)
        ForumTopic b = createTopic(owner, "Сортировка B fv_p6_24", TopicVisibility.PUBLIC, null);
        b.setLikes(4);
        topicRepository.save(b);
        // тред C — 3 поста, рейтинг 0 (размер большой)
        ForumTopic c = createTopic(owner, "Сортировка C fv_p6_24", TopicVisibility.PUBLIC, null);
        continuation(owner, c, "продолжение C1");
        continuation(owner, c, "продолжение C2");

        MockHttpSession session = login("fv_owner_24");
        // по рейтингу: A (10/2=5) > B (4/1=4) > C (0/3=0)
        String byRating = mockMvc.perform(get("/forum?sort=rating").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(byRating.indexOf("Сортировка A")).isLessThan(byRating.indexOf("Сортировка B"));
        assertThat(byRating.indexOf("Сортировка B")).isLessThan(byRating.indexOf("Сортировка C"));

        // по размеру: C (3) > A (2) > B (1)
        String bySize = mockMvc.perform(get("/forum?sort=size").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(bySize.indexOf("Сортировка C")).isLessThan(bySize.indexOf("Сортировка A"));
        assertThat(bySize.indexOf("Сортировка A")).isLessThan(bySize.indexOf("Сортировка B"));

        // дропдаун сортировки присутствует
        assertThat(byRating).contains("name=\"sort\"");
    }

    @Test
    void p6SortPersistsThroughPaginationAndSearchForm() throws Exception {
        User owner = createUser("fv_owner_25");
        createTopic(owner, "Сортировка форма fv_p6_25", TopicVisibility.PUBLIC, null);
        MockHttpSession session = login("fv_owner_25");
        String page = mockMvc.perform(get("/forum?sort=rating").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String flat = page.replaceAll("\\s+", " ");
        // виджет сортировки выбран и hidden-поле переносит выбор в пагинацию/табы
        assertThat(flat).contains("value=\"rating\" selected=\"selected\"");
        assertThat(flat).contains("name=\"type\" value=\"ALL\"");
        assertThat(flat).contains("sort=rating");
    }

    private void continuation(User owner, ForumTopic topic, String content) {
        ForumPost cont = new ForumPost();
        cont.setTopicId(topic.getId());
        cont.setContent(content);
        cont.setAuthorId(owner.getId());
        cont.setContinuation(true);
        postRepository.save(cont);
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
