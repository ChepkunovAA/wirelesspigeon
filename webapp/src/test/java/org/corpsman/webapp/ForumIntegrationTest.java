package org.corpsman.webapp;

import org.corpsman.auth.model.User;
import org.corpsman.auth.model.UserRole;
import org.corpsman.auth.repository.UserRepository;
import org.corpsman.forum.model.ForumPost;
import org.corpsman.forum.model.ForumTag;
import org.corpsman.forum.model.ForumTopic;
import org.corpsman.forum.model.TopicBan;
import org.corpsman.forum.repository.ForumAttachmentRepository;
import org.corpsman.forum.repository.ForumPostRepository;
import org.corpsman.forum.repository.ForumTagRepository;
import org.corpsman.forum.repository.ForumTopicRepository;
import org.corpsman.forum.repository.TopicBanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ForumIntegrationTest {

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
    private ForumTagRepository tagRepository;

    @Autowired
    private TopicBanRepository banRepository;

    @Autowired
    private ForumAttachmentRepository attachmentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void clearCaches() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @Test
    void forumPagesRequireAuthentication() throws Exception {
        // 2.2.0 п.1: чтение форума доступно анониму, запись — нет
        mockMvc.perform(get("/forum")).andExpect(status().isOk());
        // аноним не может писать: с CSRF-токеном -> редирект на логин, без -> 403
        mockMvc.perform(post("/forum/topic/1/comment").with(csrf()))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/forum/topic/1/comment"))
                .andExpect(status().isForbidden());
    }

    @Test
    void closedNonReadOnlyThreadAcceptsCommentFromWhitelistedNonOwner() throws Exception {
        // Закрытый тред без read-only: писать может автор и участник белого списка.
        createUser("auto_forum_closed_owner");
        createUser("auto_forum_closed_guest");
        MockHttpSession owner = login("auto_forum_closed_owner");

        String location = mockMvc.perform(post("/forum/topic/create").session(owner).with(csrf())
                        .param("title", "Закрытое обсуждение")
                        .param("content", "Текст закрытого треда для проверки белого списка.")
                        .param("visibility", "CLOSED")
                        .param("readOnly", "false"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        Long topicId = topicIdFrom(location);

        mockMvc.perform(post("/forum/topic/" + topicId + "/whitelist").session(owner).with(csrf())
                        .param("whitelistItems", "auto_forum_closed_guest"))
                .andExpect(status().is3xxRedirection());

        MockHttpSession guest = login("auto_forum_closed_guest");
        mockMvc.perform(post("/forum/topic/" + topicId + "/comment").session(guest).with(csrf())
                        .param("content", "Комментарий из белого списка"))
                .andExpect(status().is3xxRedirection());
        assertThat(postRepository.findByTopicIdOrderByCreatedAtAsc(topicId))
                .extracting(ForumPost::getContent)
                .contains("Комментарий из белого списка");
    }

    @Test
    void readOnlyThreadRejectsCommentEvenForWhitelistedNonOwner() throws Exception {
        // read-only означает «писать может только автор»: белый список даёт
        // видимость и право писать в закрытых тредах, но не снимает read-only.
        createUser("auto_forum_ro_owner");
        createUser("auto_forum_ro_guest");
        MockHttpSession owner = login("auto_forum_ro_owner");

        String location = mockMvc.perform(post("/forum/topic/create").session(owner).with(csrf())
                        .param("title", "Закрытое обсуждение только для чтения")
                        .param("content", "Тред, доступный только автору для записи.")
                        .param("visibility", "CLOSED")
                        .param("readOnly", "true"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        Long topicId = topicIdFrom(location);

        mockMvc.perform(post("/forum/topic/" + topicId + "/whitelist").session(owner).with(csrf())
                        .param("whitelistItems", "auto_forum_ro_guest"))
                .andExpect(status().is3xxRedirection());

        MockHttpSession guest = login("auto_forum_ro_guest");
        mockMvc.perform(post("/forum/topic/" + topicId + "/comment").session(guest).with(csrf())
                        .param("content", "Комментарий в read-only тред"))
                .andExpect(status().isForbidden());

        // автор в своём read-only треде писать может
        mockMvc.perform(post("/forum/topic/" + topicId + "/comment").session(owner).with(csrf())
                        .param("content", "Комментарий автора"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void adminInClosedTopicWhitelistCanComment() throws Exception {
        // Администратор, добавленный в белый список закрытого треда, писать может:
        // проверка доступа не должна отсекать MASTER раньше проверки списка.
        createUser("auto_forum_admin_topic_owner");
        createUser("auto_forum_admin");
        User admin = userRepository.findByUsername("auto_forum_admin").orElseThrow();
        admin.setRole(UserRole.MASTER);
        userRepository.save(admin);

        MockHttpSession owner = login("auto_forum_admin_topic_owner");
        String location = mockMvc.perform(post("/forum/topic/create").session(owner).with(csrf())
                        .param("title", "Закрытый тред с админом в списке")
                        .param("content", "Администратор состоит в белом списке.")
                        .param("visibility", "CLOSED")
                        .param("readOnly", "false")
                        .param("whitelistItems", "auto_forum_admin"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        Long topicId = topicIdFrom(location);

        MockHttpSession adminSession = login("auto_forum_admin");
        mockMvc.perform(post("/forum/topic/" + topicId + "/comment").session(adminSession).with(csrf())
                        .param("content", "Комментарий администратора из белого списка"))
                .andExpect(status().is3xxRedirection());
        assertThat(postRepository.findByTopicIdOrderByCreatedAtAsc(topicId))
                .extracting(ForumPost::getContent)
                .contains("Комментарий администратора из белого списка");
    }

    @Test
    void adminNotInClosedTopicWhitelistCannotComment() throws Exception {
        // Администратор без членства в белом списке закрытого треда не пишет.
        createUser("auto_forum_admin_out_topic_owner");
        createUser("auto_forum_admin_out");
        User admin = userRepository.findByUsername("auto_forum_admin_out").orElseThrow();
        admin.setRole(UserRole.MASTER);
        userRepository.save(admin);

        MockHttpSession owner = login("auto_forum_admin_out_topic_owner");
        String location = mockMvc.perform(post("/forum/topic/create").session(owner).with(csrf())
                        .param("title", "Закрытый тред без админа в списке")
                        .param("content", "Администратор не состоит в белом списке.")
                        .param("visibility", "CLOSED")
                        .param("readOnly", "false"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        Long topicId = topicIdFrom(location);

        MockHttpSession adminSession = login("auto_forum_admin_out");
        mockMvc.perform(post("/forum/topic/" + topicId + "/comment").session(adminSession).with(csrf())
                        .param("content", "Комментарий администратора не из списка"))
                .andExpect(status().isForbidden());
    }

    private Long topicIdFrom(String redirectedUrl) {
        Matcher m = Pattern.compile(".*/(\\d+).*").matcher(redirectedUrl);
        assertTrue(m.matches(), "не удалось разобрать id треда из редиректа: " + redirectedUrl);
        return Long.valueOf(m.group(1));
    }

    @Test
    void createTopicRedirectsToTopicAndRenders() throws Exception {
        createUser("auto_for_alice_1");
        MockHttpSession session = login("auto_for_alice_1");

        mockMvc.perform(post("/forum/topic/create").session(session).with(csrf())
                        .param("title", "Куда пойти на выходных")
                        .param("content", "Предлагаю обсудить варианты активного отдыха на эти выходные.")
                        .param("tags", "выходные, отдых"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/forum/topic/*"));

        ForumTopic topic = topicRepository.findAll().iterator().next();
        mockMvc.perform(get("/forum/topic/" + topic.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Куда пойти на выходных")))
                .andExpect(content().string(containsString("варианты активного отдыха")))
                .andExpect(content().string(containsString("Оставить комментарий")));
    }

    @Test
    void topicPageShowsScrollToBottomButton() throws Exception {
        createUser("auto_for_scroll_1");
        MockHttpSession session = login("auto_for_scroll_1");

        mockMvc.perform(post("/forum/topic/create").session(session).with(csrf())
                        .param("title", "Тред с кнопкой прокрутки")
                        .param("content", "Контент.")
                        .param("tags", "тест"))
                .andExpect(status().is3xxRedirection());

        ForumTopic topic = topicRepository.findAll().iterator().next();
        mockMvc.perform(get("/forum/topic/" + topic.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"scroll-bottom-btn\"")))
                .andExpect(content().string(containsString("id=\"comment-form\"")));
    }

    @Test
    void topicListShowsCreatedThread() throws Exception {
        User alice = createUser("auto_for_alice_2");
        ForumTopic topic = addTopic(alice, "Читальный клуб", false);
        MockHttpSession session = login("auto_for_alice_2");

        mockMvc.perform(get("/forum").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Читальный клуб")))
                .andExpect(content().string(containsString("/forum/topic/" + topic.getId())));
    }

    @Test
    void topicCardPreviewShowsLatestPostNotComment() throws Exception {
        User alice = createUser("auto_for_preview_2");
        ForumTopic topic = addTopic(alice, "Карточка с превью", false);

        ForumPost op = new ForumPost();
        op.setTopicId(topic.getId());
        op.setParentPostId(null);
        op.setContent("Содержимое стартового поста карточки");
        op.setAuthorId(alice.getId());
        op.setAnonymous(false);
        op.setDeleted(false);
        op.setOp(true);
        postRepository.save(op);

        ForumPost comment = addPost(topic, alice, "Свежий комментарий после стартового поста");
        assertThat(comment.getId()).isGreaterThan(op.getId());

        MockHttpSession session = login("auto_for_preview_2");
        String page = mockMvc.perform(get("/forum").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(page)
                .contains("Содержимое стартового поста карточки")
                .doesNotContain("Свежий комментарий после стартового поста");
    }

    @Test
    void addCommentAndReplyBuildTree() throws Exception {
        User alice = createUser("auto_for_alice_3");
        User bob = createUser("auto_for_bob_3");
        ForumTopic topic = addTopic(alice, "Киноклуб", false);
        MockHttpSession session = login("auto_for_bob_3");

        mockMvc.perform(post("/forum/topic/" + topic.getId() + "/comment").session(session).with(csrf())
                        .param("content", "Отличная идея, поддерживаю!"))
                .andExpect(status().is3xxRedirection());

        ForumPost root = postRepository.findByTopicIdOrderByCreatedAtAsc(topic.getId()).get(0);

        mockMvc.perform(post("/forum/topic/" + topic.getId() + "/comment").session(session).with(csrf())
                        .param("content", "И я тоже за.")
                        .param("parentId", String.valueOf(root.getId())))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/forum/topic/" + topic.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Отличная идея, поддерживаю!")))
                .andExpect(content().string(containsString("И я тоже за.")));
        assertThat(postRepository.countByTopicId(topic.getId())).isEqualTo(2);
    }

    @Test
    void replyButtonCreatesFlatCommentAtParentLevel() throws Exception {
        User alice = createUser("auto_for_alice_19");
        User bob = createUser("auto_for_bob_19");
        User charlie = createUser("auto_for_charlie_19");
        ForumTopic topic = addTopic(alice, "Гастрономия", false);

        MockHttpSession bobSession = login("auto_for_bob_19");
        mockMvc.perform(post("/forum/topic/" + topic.getId() + "/comment").session(bobSession).with(csrf())
                        .param("content", "Корневой комментарий"))
                .andExpect(status().is3xxRedirection());
        ForumPost root = postRepository.findByTopicIdOrderByCreatedAtAsc(topic.getId()).get(0);

        mockMvc.perform(post("/forum/topic/" + topic.getId() + "/comment").session(bobSession).with(csrf())
                        .param("content", "Вложенный ответ")
                        .param("parentId", String.valueOf(root.getId())))
                .andExpect(status().is3xxRedirection());
        ForumPost nested = postRepository.findByTopicIdOrderByCreatedAtAsc(topic.getId()).get(1);

        MockHttpSession charlieSession = login("auto_for_charlie_19");

        mockMvc.perform(post("/forum/topic/" + topic.getId() + "/comment").session(charlieSession).with(csrf())
                        .param("content", "Плоский ответ на корень")
                        .param("parentId", ""))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/forum/topic/" + topic.getId() + "/comment").session(charlieSession).with(csrf())
                        .param("content", "Плоский ответ на вложенный")
                        .param("parentId", String.valueOf(nested.getParentPostId())))
                .andExpect(status().is3xxRedirection());

        List<ForumPost> posts = postRepository.findByTopicIdOrderByCreatedAtAsc(topic.getId());
        assertThat(posts).hasSize(4);
        assertThat(posts.get(2).getParentPostId()).isNull();
        assertThat(posts.get(3).getParentPostId()).isEqualTo(nested.getParentPostId());
    }

    @Test
    void flatReplyShowsParentAndSubtreeCount() throws Exception {
        User alice = createUser("auto_for_alice_20");
        User bob = createUser("auto_for_bob_20");
        User charlie = createUser("auto_for_charlie_20");
        ForumTopic topic = addTopic(alice, "Путешествия", false);

        MockHttpSession bobSession = login("auto_for_bob_20");
        mockMvc.perform(post("/forum/topic/" + topic.getId() + "/comment").session(bobSession).with(csrf())
                        .param("content", "Куда поедем?"))
                .andExpect(status().is3xxRedirection());
        ForumPost root = postRepository.findByTopicIdOrderByCreatedAtAsc(topic.getId()).get(0);

        MockHttpSession charlieSession = login("auto_for_charlie_20");
        mockMvc.perform(post("/forum/topic/" + topic.getId() + "/comment").session(charlieSession).with(csrf())
                        .param("content", "Может в горы")
                        .param("parentId", "")
                        .param("replyToId", String.valueOf(root.getId())))
                .andExpect(status().is3xxRedirection());

        ForumPost flat = postRepository.findByTopicIdOrderByCreatedAtAsc(topic.getId()).get(1);
        assertThat(flat.getReplyToId()).isEqualTo(root.getId());

        mockMvc.perform(get("/forum/topic/" + topic.getId()).session(charlieSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ответ на комментарий")))
                .andExpect(content().string(containsString("Куда поедем?")));

        mockMvc.perform(post("/forum/topic/" + topic.getId() + "/comment").session(charlieSession).with(csrf())
                        .param("content", "Вложенный")
                        .param("parentId", String.valueOf(flat.getId())))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/forum/topic/" + topic.getId()).session(charlieSession))
                .andExpect(status().isOk())
                .andExpect(content().string(matchesPattern("(?s).*Раскрыть\\s+<span>1</span>\\s+<span>комментарий</span>.*")));
    }

    @Test
    void anonymousCommentShowsAnonym() throws Exception {
        User alice = createUser("auto_for_alice_4");
        User bob = createUser("auto_for_bob_4");
        ForumTopic topic = addTopic(alice, "Кулинария", false);
        MockHttpSession session = login("auto_for_bob_4");

        mockMvc.perform(post("/forum/topic/" + topic.getId() + "/comment").session(session).with(csrf())
                        .param("content", "Секретный рецепт, не буду представляться.")
                        .param("anonymous", "true"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/forum/topic/" + topic.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Секретный рецепт")))
                .andExpect(content().string(containsString("Аноним")));
    }

    @Test
    void likeAndUnlikePost() throws Exception {
        User alice = createUser("auto_for_alice_5");
        User bob = createUser("auto_for_bob_5");
        ForumTopic topic = addTopic(alice, "Спорт", false);
        ForumPost post = addPost(topic, alice, "Кто бегает по утрам?");
        MockHttpSession session = login("auto_for_bob_5");

        mockMvc.perform(post("/forum/post/" + post.getId() + "/like").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likes").value(1))
                .andExpect(jsonPath("$.liked").value(true));

        mockMvc.perform(post("/forum/post/" + post.getId() + "/unlike").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likes").value(0))
                .andExpect(jsonPath("$.liked").value(false));
    }

    @Test
    void cannotLikeOwnPost() throws Exception {
        User alice = createUser("auto_for_alice_6");
        ForumTopic topic = addTopic(alice, "Музыка", false);
        ForumPost post = addPost(topic, alice, "Мой пост");
        MockHttpSession session = login("auto_for_alice_6");

        mockMvc.perform(post("/forum/post/" + post.getId() + "/like").session(session).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("Нельзя лайкнуть свой пост")));
    }

    @Test
    void frozenTopicBlocksComments() throws Exception {
        User alice = createUser("auto_for_alice_7");
        User bob = createUser("auto_for_bob_7");
        ForumTopic topic = addTopic(alice, "Замороженный", false);
        topic.setFrozen(true);
        topicRepository.save(topic);
        MockHttpSession session = login("auto_for_bob_7");

        mockMvc.perform(post("/forum/topic/" + topic.getId() + "/comment").session(session).with(csrf())
                        .param("content", "Попытка написать"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("заморожен")));
    }

    @Test
    void authorCanDeleteOwnPost() throws Exception {
        User alice = createUser("auto_for_alice_8");
        User bob = createUser("auto_for_bob_8");
        ForumTopic topic = addTopic(alice, "Фотография", false);
        ForumPost post = addPost(topic, bob, "Поделитесь лайфхаками");
        MockHttpSession session = login("auto_for_bob_8");

        mockMvc.perform(post("/forum/post/" + post.getId() + "/delete").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/forum/topic/" + topic.getId()));

        ForumPost deleted = postRepository.findById(post.getId()).orElseThrow();
        assertThat(deleted.isDeleted()).isTrue();
        assertThat(deleted.getContent()).isEqualTo("[Удалено]");
    }

    @Test
    void bannedUserCannotComment() throws Exception {
        User alice = createUser("auto_for_alice_9");
        User bob = createUser("auto_for_bob_9");
        ForumTopic topic = addTopic(alice, "Игры", false);
        TopicBan ban = new TopicBan();
        ban.setTopicId(topic.getId());
        ban.setUserId(bob.getId());
        ban.setBannedBy(alice.getId());
        ban.setReason("Спам");
        banRepository.save(ban);
        MockHttpSession session = login("auto_for_bob_9");

        mockMvc.perform(post("/forum/topic/" + topic.getId() + "/comment").session(session).with(csrf())
                        .param("content", "Меня забанили, но я всё равно пишу"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("забанены")));
    }

    @Test
    void moderatorCanDeleteForeignPost() throws Exception {
        User alice = createUser("auto_for_alice_10");
        User bob = createUser("auto_for_bob_10");
        ForumTopic topic = addTopic(alice, "Путешествия", false);
        ForumPost post = addPost(topic, bob, "Куда слетать зимой?");
        MockHttpSession session = login("auto_for_alice_10");

        mockMvc.perform(post("/forum/post/" + post.getId() + "/delete").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(postRepository.findById(post.getId()).orElseThrow().isDeleted()).isTrue();
    }

    @Test
    void deletedCommentWithoutSubtreeDisappears() throws Exception {
        User alice = createUser("auto_for_alice_21");
        User bob = createUser("auto_for_bob_21");
        ForumTopic topic = addTopic(alice, "Автомобили", false);
        MockHttpSession session = login("auto_for_bob_21");

        mockMvc.perform(post("/forum/topic/" + topic.getId() + "/comment").session(session).with(csrf())
                        .param("content", "Комментарий без поддерева"))
                .andExpect(status().is3xxRedirection());
        ForumPost orphan = postRepository.findByTopicIdOrderByCreatedAtAsc(topic.getId()).get(0);

        mockMvc.perform(post("/forum/topic/" + topic.getId() + "/comment").session(session).with(csrf())
                        .param("content", "Комментарий с поддеревом"))
                .andExpect(status().is3xxRedirection());
        ForumPost withSubtree = postRepository.findByTopicIdOrderByCreatedAtAsc(topic.getId()).get(1);

        mockMvc.perform(post("/forum/topic/" + topic.getId() + "/comment").session(session).with(csrf())
                        .param("content", "Ответ в ветку")
                        .param("parentId", String.valueOf(withSubtree.getId())))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/forum/post/" + orphan.getId() + "/delete").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/forum/post/" + withSubtree.getId() + "/delete").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/forum/topic/" + topic.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Комментарий без поддерева"))))
                .andExpect(content().string(not(containsString("Комментарий с поддеревом"))))
                .andExpect(content().string(not(containsString("Ответ в ветку"))))
                .andExpect(content().string(not(containsString("[Удалено]"))))
                .andExpect(content().string(containsString("(<span>0</span>)")));
    }

    @Test
    void ownerDeletesCommentWithoutSubtreeShowsPlaceholder() throws Exception {
        User alice = createUser("auto_for_alice_22");
        User bob = createUser("auto_for_bob_22");
        ForumTopic topic = addTopic(alice, "Спорт", false);
        ForumPost post = addPost(topic, bob, "Без поддерева, удалит владелец");
        MockHttpSession ownerSession = login("auto_for_alice_22");

        mockMvc.perform(post("/forum/post/" + post.getId() + "/delete").session(ownerSession).with(csrf()))
                .andExpect(status().is3xxRedirection());

        ForumPost deleted = postRepository.findById(post.getId()).orElseThrow();
        assertThat(deleted.isDeleted()).isTrue();
        assertThat(deleted.isDeletedByOwner()).isTrue();

        mockMvc.perform(get("/forum/topic/" + topic.getId()).session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Удалено владельцем треда")));
    }

    @Test
    void adminDeletesCommentWithoutSubtreeShowsPlaceholder() throws Exception {
        User alice = createUser("auto_for_alice_23");
        User admin = createUser("auto_for_admin_23");
        admin.setRole(UserRole.MASTER);
        userRepository.save(admin);
        ForumTopic topic = addTopic(alice, "Кино", false);
        ForumPost post = addPost(topic, alice, "Без поддерева, удалит админ");
        MockHttpSession adminSession = login("auto_for_admin_23");

        mockMvc.perform(post("/forum/post/" + post.getId() + "/delete").session(adminSession).with(csrf()))
                .andExpect(status().is3xxRedirection());

        ForumPost deleted = postRepository.findById(post.getId()).orElseThrow();
        assertThat(deleted.isDeleted()).isTrue();
        assertThat(deleted.isDeletedByAdmin()).isTrue();

        mockMvc.perform(get("/forum/topic/" + topic.getId()).session(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Удалено администратором")));
    }

    @Test
    void adminCreatesTopicAnonymouslyAndSeesRealAuthor() throws Exception {
        User admin = createUser("auto_for_admin_11");
        admin.setRole(UserRole.MASTER);
        userRepository.save(admin);
        MockHttpSession session = login("auto_for_admin_11");

        mockMvc.perform(post("/forum/topic/create").session(session).with(csrf())
                        .param("title", "Анонимное объявление")
                        .param("content", "Служебная информация для всех участников клуба.")
                        .param("anonymous", "true"))
                .andExpect(status().is3xxRedirection());

        ForumTopic topic = topicRepository.findAll().iterator().next();
        mockMvc.perform(get("/forum/topic/" + topic.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Аноним")))
                .andExpect(content().string(containsString("auto_for_admin_11")));
    }

    @Test
    void adminDoesNotSeeRealAuthorForNonAnonymousTopic() throws Exception {
        User admin = createUser("auto_for_admin_12");
        admin.setRole(UserRole.MASTER);
        userRepository.save(admin);
        User alice = createUser("auto_for_alice_12");
        ForumTopic topic = addTopic(alice, "Обычный тред", false);
        MockHttpSession session = login("auto_for_admin_12");

        mockMvc.perform(get("/forum/topic/" + topic.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("auto_for_alice_12")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Реальный автор:"))));
    }

    @Test
    void dislikeAndUndislikePost() throws Exception {
        User alice = createUser("auto_for_alice_13");
        User bob = createUser("auto_for_bob_13");
        ForumTopic topic = addTopic(alice, "Велосипеды", false);
        ForumPost post = addPost(topic, alice, "Кто ездит на работу на велосипеде?");
        MockHttpSession session = login("auto_for_bob_13");

        mockMvc.perform(post("/forum/post/" + post.getId() + "/dislike").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dislikes").value(1))
                .andExpect(jsonPath("$.disliked").value(true));

        mockMvc.perform(post("/forum/post/" + post.getId() + "/undislike").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dislikes").value(0))
                .andExpect(jsonPath("$.disliked").value(false));
    }

    @Test
    void likeRemovesDislikeOnPost() throws Exception {
        User alice = createUser("auto_for_alice_14");
        User bob = createUser("auto_for_bob_14");
        ForumTopic topic = addTopic(alice, "Книги", false);
        ForumPost post = addPost(topic, alice, "Что читаете?");
        MockHttpSession session = login("auto_for_bob_14");

        mockMvc.perform(post("/forum/post/" + post.getId() + "/dislike").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disliked").value(true));

        mockMvc.perform(post("/forum/post/" + post.getId() + "/like").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(true))
                .andExpect(jsonPath("$.disliked").value(false))
                .andExpect(jsonPath("$.dislikes").value(0));
    }

    @Test
    void likeAndDislikeFirstPostOfTopic() throws Exception {
        User alice = createUser("auto_for_alice_15");
        User bob = createUser("auto_for_bob_15");
        ForumTopic topic = addTopic(alice, "Кулинария и жизнь", false);
        MockHttpSession session = login("auto_for_bob_15");

        mockMvc.perform(post("/forum/topic/" + topic.getId() + "/like").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likes").value(1))
                .andExpect(jsonPath("$.liked").value(true));

        mockMvc.perform(post("/forum/topic/" + topic.getId() + "/dislike").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liked").value(false))
                .andExpect(jsonPath("$.disliked").value(true))
                .andExpect(jsonPath("$.dislikes").value(1));

        mockMvc.perform(get("/forum/topic/" + topic.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("hand-thumbs-down")));
    }

    @Test
    void cannotDislikeOwnPost() throws Exception {
        User alice = createUser("auto_for_alice_16");
        ForumTopic topic = addTopic(alice, "Творчество", false);
        ForumPost post = addPost(topic, alice, "Мой шедевр");
        MockHttpSession session = login("auto_for_alice_16");

        mockMvc.perform(post("/forum/post/" + post.getId() + "/dislike").session(session).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("Нельзя поставить дизлайк своему посту")));
    }

    @Test
    void moderatorCanBanUserFromPost() throws Exception {
        User alice = createUser("auto_for_alice_17");
        User bob = createUser("auto_for_bob_17");
        ForumTopic topic = addTopic(alice, "Маркетинг", false);
        ForumPost post = addPost(topic, bob, "Назойливый комментарий");
        MockHttpSession session = login("auto_for_alice_17");

        mockMvc.perform(post("/forum/topic/" + topic.getId() + "/moderation/ban").session(session).with(csrf())
                        .param("userId", String.valueOf(bob.getId())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/forum/topic/" + topic.getId()));

        assertThat(banRepository.existsByTopicIdAndUserId(topic.getId(), bob.getId())).isTrue();
    }

    @Test
    void bannedListShowsAnonymousInsteadOfRealName() throws Exception {
        User alice = createUser("auto_for_alice_18");
        User bob = createUser("auto_for_bob_18");
        ForumTopic topic = addTopic(alice, "Недвижимость", false);
        ForumPost post = new ForumPost();
        post.setTopicId(topic.getId());
        post.setParentPostId(null);
        post.setContent("Анонимный комментарий");
        post.setAuthorId(bob.getId());
        post.setAnonymous(true);
        post.setDeleted(false);
        post.setEdited(false);
        post.setLikes(0);
        postRepository.save(post);
        MockHttpSession session = login("auto_for_alice_18");

        mockMvc.perform(post("/forum/topic/" + topic.getId() + "/moderation/ban").session(session).with(csrf())
                        .param("userId", String.valueOf(bob.getId()))
                        .param("displayName", "Аноним"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/forum/topic/" + topic.getId()));

        mockMvc.perform(get("/forum/topic/" + topic.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Аноним")))
                .andExpect(content().string(not(containsString("auto_for_bob_18"))));
    }



    @Test
    void commentRedirectsToPostAnchor() throws Exception {
        User alice = createUser("auto_for_alice_22");
        ForumTopic topic = addTopic(alice, "Скролл", false);
        MockHttpSession session = login("auto_for_alice_22");

        MvcResult result = mockMvc.perform(post("/forum/topic/" + topic.getId() + "/comment").session(session).with(csrf())
                        .param("content", "Комментарий с якорем"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String location = result.getResponse().getRedirectedUrl();
        assertThat(location).isNotNull();
        assertThat(location).matches("/forum/topic/\\d+#post-\\d+");

        long postId = postRepository.findByTopicIdOrderByCreatedAtAsc(topic.getId()).get(0).getId();
        assertThat(location).endsWith("#post-" + postId);

        mockMvc.perform(get("/forum/topic/" + topic.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"post-" + postId + "\"")));
    }

    @Test
    void threadReplyRedirectsWithOpenParam() throws Exception {
        User alice = createUser("auto_for_alice_23");
        User bob = createUser("auto_for_bob_23");
        ForumTopic topic = addTopic(alice, "Ветки", false);
        ForumPost root = addPost(topic, bob, "Корневой комментарий");
        MockHttpSession session = login("auto_for_bob_23");

        MvcResult result = mockMvc.perform(post("/forum/topic/" + topic.getId() + "/comment").session(session).with(csrf())
                        .param("content", "Ответ в ветку")
                        .param("parentId", String.valueOf(root.getId())))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String location = result.getResponse().getRedirectedUrl();
        assertThat(location).isNotNull();
        assertThat(location).matches("/forum/topic/\\d+\\?open=\\d+#post-\\d+");
        assertThat(location).contains("?open=" + root.getId());
    }

    @Test
    void createTopicWithSingleCharTitleAndShortContent() throws Exception {
        createUser("auto_for_alice_24");
        MockHttpSession session = login("auto_for_alice_24");

        mockMvc.perform(post("/forum/topic/create").session(session).with(csrf())
                        .param("title", "A")
                        .param("content", "B"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/forum/topic/*"));
    }


    @Test
    void publicAttachmentEndpointRejectsPrivateTopic() throws Exception {
        createUser("auto_for_alice_28");
        byte[] image = {1, 2, 3};

        mockMvc.perform(multipart("/forum/topic/create")
                        .file(new MockMultipartFile("files", "secret.png", "image/png", image))
                        .param("title", "Закрытый тред")
                        .param("content", "Материалы закрытого обсуждения.")
                        .session(login("auto_for_alice_28")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        ForumTopic topic = topicRepository.findAll().iterator().next();
        long attachmentId = attachmentRepository.findByTopicIdOrderByIdAsc(topic.getId()).get(0).getId();

        mockMvc.perform(get("/forum/public/attachment/" + attachmentId))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/forum/attachment/" + attachmentId))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void commentWithImageAttachmentRendersAndServesFile() throws Exception {
        User alice = createUser("auto_for_alice_25");
        ForumTopic topic = addTopic(alice, "Фотографии", false);
        MockHttpSession session = login("auto_for_alice_25");
        byte[] image = {1, 2, 3, 4, 5};

        mockMvc.perform(multipart("/forum/topic/" + topic.getId() + "/comment")
                        .file(new MockMultipartFile("files", "photo.png", "image/png", image))
                        .param("content", "Смотрим фото")
                        .session(session).with(csrf()))
                .andExpect(status().is3xxRedirection());

        ForumPost post = postRepository.findByTopicIdOrderByCreatedAtAsc(topic.getId()).get(0);
        long attachmentId = attachmentRepository.findByPostIdOrderByIdAsc(post.getId()).get(0).getId();

        mockMvc.perform(get("/forum/topic/" + topic.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/forum/attachment/" + attachmentId)));

        mockMvc.perform(get("/forum/attachment/" + attachmentId).session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("image/png"))
                .andExpect(content().bytes(image));
    }

    @Test
    void topicWithFileAttachmentServesDownload() throws Exception {
        createUser("auto_for_alice_26");
        MockHttpSession session = login("auto_for_alice_26");
        byte[] data = "hello".getBytes();

        mockMvc.perform(multipart("/forum/topic/create")
                        .file(new MockMultipartFile("files", "notes.txt", "text/plain", data))
                        .param("title", "Заметки клуба")
                        .param("content", "Полезные материалы для всех участников клуба.")
                        .session(session).with(csrf()))
                .andExpect(status().is3xxRedirection());

        ForumTopic topic = topicRepository.findAll().iterator().next();
        long attachmentId = attachmentRepository.findByTopicIdOrderByIdAsc(topic.getId()).get(0).getId();

        mockMvc.perform(get("/forum/topic/" + topic.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/forum/attachment/" + attachmentId)))
                .andExpect(content().string(containsString("notes.txt")));

        mockMvc.perform(get("/forum/attachment/" + attachmentId).session(session))
                .andExpect(status().isOk())
                .andExpect(content().bytes(data));
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
                .andExpect(redirectedUrl("/"))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private ForumTopic addTopic(User author, String title, boolean anonymous) {
        ForumTopic topic = new ForumTopic();
        topic.setTitle(title);
        topic.setContent("Начальное содержание треда для обсуждения и комментирования.");
        topic.setAuthorId(author.getId());
        topic.setAnonymous(anonymous);
        topic.setFrozen(false);
        topic.setDeleted(false);
        topic.setLastActivityAt(LocalDateTime.now());
        ForumTag tag = tagRepository.findByName("общее").orElseGet(() -> {
            ForumTag t = new ForumTag();
            t.setName("общее");
            return tagRepository.save(t);
        });
        topic.setTags(new java.util.HashSet<>(Set.of(tag)));
        return topicRepository.save(topic);
    }

    private ForumPost addPost(ForumTopic topic, User author, String content) {
        ForumPost post = new ForumPost();
        post.setTopicId(topic.getId());
        post.setParentPostId(null);
        post.setContent(content);
        post.setAuthorId(author.getId());
        post.setAnonymous(false);
        post.setDeleted(false);
        post.setEdited(false);
        post.setLikes(0);
        return postRepository.save(post);
    }
}