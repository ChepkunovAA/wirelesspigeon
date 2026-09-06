package org.corpsman.forum.service;

import org.corpsman.auth.model.User;
import org.corpsman.auth.model.UserRole;
import org.corpsman.auth.model.UserSettings;
import org.corpsman.auth.repository.UserRepository;
import org.corpsman.common.event.TopicCreatedEvent;
import org.corpsman.common.exception.BusinessException;
import org.corpsman.common.exception.ErrorCode;
import org.corpsman.forum.dto.TopicCreateRequest;
import org.corpsman.forum.dto.TopicDetailDto;
import org.corpsman.forum.dto.TopicSummaryDto;
import org.corpsman.forum.dto.ForumPostDto;
import org.corpsman.forum.model.ForumPost;
import org.corpsman.forum.model.ForumTag;
import org.corpsman.forum.model.ForumTopic;
import org.corpsman.forum.model.TopicVisibility;
import org.corpsman.forum.repository.ForumPostRepository;
import org.corpsman.forum.repository.ForumTagRepository;
import org.corpsman.forum.repository.ForumTopicRepository;
import org.corpsman.forum.repository.PostDislikeRepository;
import org.corpsman.forum.repository.PostLikeRepository;
import org.corpsman.forum.repository.TopicBanRepository;
import org.corpsman.forum.repository.TopicDislikeRepository;
import org.corpsman.forum.repository.TopicLikeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForumServiceTest {

    @Mock
    private ForumTopicRepository topicRepository;
    @Mock
    private ForumPostRepository postRepository;
    @Mock
    private ForumTagRepository tagRepository;
    @Mock
    private TopicBanRepository banRepository;
    @Mock
    private PostLikeRepository likeRepository;
    @Mock
    private PostDislikeRepository dislikeRepository;
    @Mock
    private TopicLikeRepository topicLikeRepository;
    @Mock
    private TopicDislikeRepository topicDislikeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private org.corpsman.forum.repository.TopicWhitelistRepository whitelistRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private AttachmentService attachmentService;
    @Mock
    private org.corpsman.auth.service.SettingsService settingsService;

    @InjectMocks
    private ForumService forumService;

    private ForumTopic topic(long id, long authorId, boolean anonymous, boolean frozen) {
        ForumTopic topic = new ForumTopic();
        topic.setId(id);
        topic.setTitle("Обсуждение фильма");
        topic.setContent("Текст первого поста достаточно длинный для превью.");
        topic.setAuthorId(authorId);
        topic.setAnonymous(anonymous);
        topic.setFrozen(frozen);
        topic.setDeleted(false);
        topic.setCreatedAt(LocalDateTime.now());
        topic.setLastActivityAt(LocalDateTime.now());
        topic.setTags(new HashSet<>());
        return topic;
    }

    private User user(long id, String username, UserRole role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role);
        return user;
    }

    @Test
    void createTopicSavesWithFlagsAndPublishesEvent() {
        TopicCreateRequest request = new TopicCreateRequest();
        request.setTitle("  Новый тред  ");
        request.setContent("  Содержимое треда для обсуждения  ");
        request.setAnonymous(true);
        request.setTags(List.of("кино", "новости"));

        ForumTag cinema = new ForumTag();
        cinema.setId(1L);
        cinema.setName("кино");
        ForumTag news = new ForumTag();
        news.setId(2L);
        news.setName("новости");
        when(tagRepository.findByNameIn(List.of("кино", "новости"))).thenReturn(List.of(cinema, news));
        when(topicRepository.save(any(ForumTopic.class))).thenAnswer(invocation -> {
            ForumTopic saved = invocation.getArgument(0);
            saved.setId(7L);
            return saved;
        });

        ForumTopic result = forumService.createTopic(request, 3L, null);

        ArgumentCaptor<ForumTopic> captor = ArgumentCaptor.forClass(ForumTopic.class);
        verify(topicRepository).save(captor.capture());
        ForumTopic saved = captor.getValue();
        assertThat(saved.getTitle()).isEqualTo("Новый тред");
        assertThat(saved.getContent()).isEqualTo("Содержимое треда для обсуждения");
        assertThat(saved.getAuthorId()).isEqualTo(3L);
        assertThat(saved.isAnonymous()).isTrue();
        assertThat(saved.getTags()).hasSize(2);
        verify(eventPublisher).publishEvent(any(TopicCreatedEvent.class));
    }

    @Test
    void createTopicGuestAccessMapsOpenToGuest() {
        TopicCreateRequest request = new TopicCreateRequest();
        request.setTitle("Тред с гостями");
        request.setContent("Контент");
        request.setVisibility("OPEN");
        request.setGuestAccess(true);
        when(topicRepository.save(any(ForumTopic.class))).thenAnswer(inv -> inv.getArgument(0));

        forumService.createTopic(request, 3L, null);

        ArgumentCaptor<ForumTopic> captor = ArgumentCaptor.forClass(ForumTopic.class);
        verify(topicRepository).save(captor.capture());
        assertThat(captor.getValue().getVisibility()).isEqualTo(TopicVisibility.GUEST);
    }

    @Test
    void createTopicWithoutGuestAccessKeepsOpen() {
        TopicCreateRequest request = new TopicCreateRequest();
        request.setTitle("Обычный открытый тред");
        request.setContent("Контент");
        request.setVisibility("OPEN");
        request.setGuestAccess(false);
        when(topicRepository.save(any(ForumTopic.class))).thenAnswer(inv -> inv.getArgument(0));

        forumService.createTopic(request, 3L, null);

        ArgumentCaptor<ForumTopic> captor = ArgumentCaptor.forClass(ForumTopic.class);
        verify(topicRepository).save(captor.capture());
        assertThat(captor.getValue().getVisibility()).isEqualTo(TopicVisibility.OPEN);
    }

    @Test
    void getTopicsOpenTypeFilterIncludesGuestTopics() {
        ForumTopic openTopic = topic(1L, 10L, false, false);
        openTopic.setVisibility(TopicVisibility.OPEN);
        when(topicRepository.search(any(), anyBoolean(), anyBoolean(), anyList(),
                anyBoolean(), any(), anyBoolean(), anyList(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(openTopic), PageRequest.of(0, 20), 1));
        when(postRepository.countByTopicIdIn(List.of(1L))).thenReturn(List.<Object[]>of(new Object[]{1L, 5L}));
        when(userRepository.findByIdIn(anyList())).thenReturn(List.of(user(10L, "luke", UserRole.RESIDENT)));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, "luke", UserRole.RESIDENT)));
        when(postRepository.findLatestPostEachByTopicIds(List.of(1L))).thenReturn(List.of());

        forumService.getTopics(PageRequest.of(0, 20), null, null, false, false, false, "OPEN", 10L);

        ArgumentCaptor<java.util.List<TopicVisibility>> captor = ArgumentCaptor.forClass(java.util.List.class);
        verify(topicRepository).search(any(), anyBoolean(), anyBoolean(), captor.capture(),
                anyBoolean(), any(), anyBoolean(), anyList(), any(PageRequest.class));
        assertThat(captor.getValue()).containsExactly(TopicVisibility.OPEN, TopicVisibility.GUEST);
    }

    @Test
    void getTopicsMapsSummaryWithCountsAndAnonymousAuthor() {
        ForumTopic visible = topic(1L, 10L, true, false);
        visible.setTags(Set.of());
        ForumTag tag = new ForumTag();
        tag.setName("важное");
        visible.setTags(Set.of(tag));
        when(topicRepository.search(any(), anyBoolean(), anyBoolean(), any(), anyBoolean(), any(), anyBoolean(), anyList(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(visible), PageRequest.of(0, 20), 1));
        when(postRepository.countByTopicIdIn(List.of(1L))).thenReturn(List.<Object[]>of(new Object[]{1L, 5L}));
        when(userRepository.findByIdIn(anyList())).thenReturn(List.of(user(10L, "luke", UserRole.RESIDENT)));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, "luke", UserRole.RESIDENT)));

        ForumPost latest = new ForumPost();
        latest.setId(7L);
        latest.setTopicId(1L);
        latest.setContent("Последнее сообщение в треде «Обсуждение фильма» с подписями");
        latest.setAuthorId(11L);
        latest.setAnonymous(false);
        when(postRepository.findLatestPostEachByTopicIds(List.of(1L))).thenReturn(List.of(latest));

        Page<TopicSummaryDto> result = forumService.getTopics(PageRequest.of(0, 20), "фильм", null, false, false, false, "ALL", 10L);

        assertThat(result.getContent()).hasSize(1);
        TopicSummaryDto dto = result.getContent().get(0);
        assertThat(dto.getTitle()).isEqualTo("Обсуждение фильма");
        assertThat(dto.getPostsCount()).isEqualTo(5L);
        assertThat(dto.getAuthorName()).isEqualTo("Аноним");
        assertThat(dto.getTags()).containsExactly("важное");
        assertThat(dto.isAuthor()).isTrue();
        assertThat(dto.getFirstPostPreview()).isEqualTo("Последнее сообщение в треде «Обсуждение фильма» с подписями");
    }

    @Test
    void getTopicBuildsTreeAndDetectsModerator() {
        ForumTopic topic = topic(1L, 10L, false, false);
        when(topicRepository.findById(1L)).thenReturn(Optional.of(topic));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, "luke", UserRole.RESIDENT)));
        when(userRepository.findByIdIn(anyList())).thenReturn(List.of(user(10L, "luke", UserRole.RESIDENT)));

        ForumPost root = new ForumPost();
        root.setId(1L);
        root.setTopicId(1L);
        root.setParentPostId(null);
        root.setContent("Корневой комментарий");
        root.setAuthorId(10L);
        root.setAnonymous(false);
        root.setLikes(0);
        root.setDeleted(false);
        root.setCreatedAt(LocalDateTime.now());
        ForumPost child = new ForumPost();
        child.setId(2L);
        child.setTopicId(1L);
        child.setParentPostId(1L);
        child.setContent("Ответ на корневой");
        child.setAuthorId(11L);
        child.setAnonymous(true);
        child.setLikes(3);
        child.setDeleted(false);
        child.setCreatedAt(LocalDateTime.now().plusMinutes(1));
        when(postRepository.findByTopicIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(root, child));
        when(likeRepository.findByPostIdInAndUserId(anyList(), any())).thenReturn(List.of());
        when(banRepository.findByTopicIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        TopicDetailDto dto = forumService.getTopic(1L, 10L, PageRequest.of(0, 20));

        assertThat(dto.getPosts().getContent()).hasSize(1);
        assertThat(dto.getPosts().getContent().get(0).getReplies()).hasSize(1);
        assertThat(dto.getPosts().getContent().get(0).getReplies().get(0).getAuthorName()).isEqualTo("Аноним");
        assertThat(dto.getPosts().getContent().get(0).getReplies().get(0).getLikes()).isEqualTo(3);
        assertThat(dto.isCanModerate()).isTrue();
        assertThat(dto.isAuthor()).isTrue();
    }

    @Test
    void countsExcludeDeletedPosts() {
        ForumTopic topic = topic(1L, 10L, false, false);
        when(topicRepository.findById(1L)).thenReturn(Optional.of(topic));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, "luke", UserRole.RESIDENT)));
        when(userRepository.findByIdIn(anyList())).thenReturn(List.of(user(10L, "luke", UserRole.RESIDENT)));

        ForumPost root = post(1L, 1L, null, "Корневой", 10L, false, false);
        ForumPost activeChild = post(2L, 1L, 1L, "Живой ответ", 11L, false, false);
        ForumPost deletedChild = post(3L, 1L, 1L, "[Удалено]", 12L, true, false);
        when(postRepository.findByTopicIdOrderByCreatedAtAsc(1L))
                .thenReturn(List.of(root, activeChild, deletedChild));
        when(likeRepository.findByPostIdInAndUserId(anyList(), any())).thenReturn(List.of());
        when(banRepository.findByTopicIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        TopicDetailDto dto = forumService.getTopic(1L, 10L, PageRequest.of(0, 20));

        assertThat(dto.getNonDeletedCount()).isEqualTo(2L);
        ForumPostDto rootDto = dto.getPosts().getContent().get(0);
        assertThat(rootDto.getReplies()).hasSize(2);
        assertThat(rootDto.getSubtreeSize()).isEqualTo(1L);
    }

    @Test
    void freezeTopicByNonModeratorFails() {
        ForumTopic topic = topic(1L, 10L, false, false);
        when(topicRepository.findById(1L)).thenReturn(Optional.of(topic));

        assertThatThrownBy(() -> forumService.freezeTopic(1L, 11L, false))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PERMISSION_DENIED);
    }

    @Test
    void banAuthorFails() {
        ForumTopic topic = topic(1L, 10L, false, false);
        when(topicRepository.findById(1L)).thenReturn(Optional.of(topic));

        assertThatThrownBy(() -> forumService.banUser(1L, 10L, 10L, false, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CANNOT_BAN_AUTHOR);
    }

    @Test
    void banAlreadyBannedUserFails() {
        ForumTopic topic = topic(1L, 10L, false, false);
        when(topicRepository.findById(1L)).thenReturn(Optional.of(topic));
        when(banRepository.existsByTopicIdAndUserId(1L, 11L)).thenReturn(true);

        assertThatThrownBy(() -> forumService.banUser(1L, 11L, 10L, false, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ALREADY_BANNED);
    }

    @Test
    void unbanNotBannedUserFails() {
        ForumTopic topic = topic(1L, 10L, false, false);
        when(topicRepository.findById(1L)).thenReturn(Optional.of(topic));
        when(banRepository.existsByTopicIdAndUserId(1L, 11L)).thenReturn(false);

        assertThatThrownBy(() -> forumService.unbanUser(1L, 11L, 10L, false))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_BANNED);
    }

    @Test
    void deleteTopicSoftDeletes() {
        ForumTopic topic = topic(1L, 10L, false, false);
        when(topicRepository.findById(1L)).thenReturn(Optional.of(topic));

        forumService.deleteTopic(1L, 10L, false);

        assertThat(topic.isDeleted()).isTrue();
        verify(topicRepository).save(topic);
    }

    @Test
    void realAuthorVisibleOnlyForAdminOnAnonymousTopic() {
        ForumTopic topic = topic(1L, 10L, true, false);
        when(topicRepository.findById(1L)).thenReturn(Optional.of(topic));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "admin", UserRole.MASTER)));
        when(userRepository.findByIdIn(anyList())).thenReturn(List.of(user(10L, "luke", UserRole.RESIDENT)));
        when(postRepository.findByTopicIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());
        when(banRepository.findByTopicIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        TopicDetailDto dto = forumService.getTopic(1L, 1L, PageRequest.of(0, 20));

        assertThat(dto.getFirstPost().getRealAuthorName()).isEqualTo("luke");
    }

    @Test
    void realAuthorNotShownForNonAnonymousTopic() {
        ForumTopic topic = topic(1L, 10L, false, false);
        when(topicRepository.findById(1L)).thenReturn(Optional.of(topic));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "admin", UserRole.MASTER)));
        when(userRepository.findByIdIn(anyList())).thenReturn(List.of(user(10L, "luke", UserRole.RESIDENT)));
        when(postRepository.findByTopicIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());
        when(banRepository.findByTopicIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        TopicDetailDto dto = forumService.getTopic(1L, 1L, PageRequest.of(0, 20));

        assertThat(dto.getFirstPost().getRealAuthorName()).isNull();
        assertThat(dto.getFirstPost().getAuthorName()).isEqualTo("luke");
    }

    @Test
    void likeTopicThenUnlikeUpdatesCount() {
        ForumTopic topic = topic(1L, 10L, false, false);
        when(topicRepository.findById(1L)).thenReturn(Optional.of(topic));
        when(topicLikeRepository.existsByTopicIdAndUserId(1L, 11L)).thenReturn(false);
        when(topicLikeRepository.findByTopicIdAndUserId(1L, 11L)).thenReturn(Optional.empty());

        org.corpsman.forum.dto.LikeResponse liked = forumService.likeTopic(1L, 11L);
        assertThat(liked.getLikes()).isEqualTo(1);
        assertThat(liked.isLiked()).isTrue();
        verify(topicRepository).incrementLikes(1L);
        verify(topicLikeRepository).save(any());

        when(topicLikeRepository.findByTopicIdAndUserId(1L, 11L)).thenReturn(Optional.of(
                new org.corpsman.forum.model.TopicLike()));
        org.corpsman.forum.dto.LikeResponse unliked = forumService.unlikeTopic(1L, 11L);
        assertThat(unliked.getLikes()).isZero();
        assertThat(unliked.isLiked()).isFalse();
        verify(topicRepository).decrementLikes(1L);
    }

    @Test
    void dislikeOwnTopicFails() {
        ForumTopic topic = topic(1L, 10L, false, false);
        when(topicRepository.findById(1L)).thenReturn(Optional.of(topic));

        assertThatThrownBy(() -> forumService.dislikeTopic(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CANNOT_DISLIKE_OWN_POST);
    }

    private ForumTopic visibleTopic(long id, long authorId, TopicVisibility visibility) {
        ForumTopic t = topic(id, authorId, false, false);
        t.setVisibility(visibility);
        return t;
    }

    private ForumPost post(long topicId, long authorId, int likes, int dislikes) {
        ForumPost p = new ForumPost();
        p.setTopicId(topicId);
        p.setContent("Содержимое поста для превью.");
        p.setAnonymous(false);
        p.setAuthorId(authorId);
        p.setOp(true);
        p.setLikes(likes);
        p.setDislikes(dislikes);
        p.setDeleted(false);
        p.setCreatedAt(LocalDateTime.now());
        return p;
    }

    private ForumPost post(long id, long topicId, Long parentPostId, String content,
                           long authorId, boolean deleted, boolean anonymous) {
        ForumPost p = new ForumPost();
        p.setId(id);
        p.setTopicId(topicId);
        p.setParentPostId(parentPostId);
        p.setContent(content);
        p.setAuthorId(authorId);
        p.setAnonymous(anonymous);
        p.setLikes(0);
        p.setDislikes(0);
        p.setDeleted(deleted);
        p.setCreatedAt(LocalDateTime.now());
        return p;
    }

    private UserSettings settings(boolean showPublic, boolean hideGuest) {
        UserSettings s = new UserSettings();
        s.setShowPublicPosts(showPublic);
        s.setHideGuestThreads(hideGuest);
        return s;
    }

    @Test
    void latestVisiblePostForAnonymousUsesOnlyPublicTopics() {
        ForumTopic pub = visibleTopic(1L, 10L, TopicVisibility.PUBLIC);
        ForumTopic closed = visibleTopic(2L, 11L, TopicVisibility.CLOSED);
        when(topicRepository.findAllByDeletedFalse()).thenReturn(List.of(pub, closed));
        when(userRepository.findByIdIn(anyList())).thenReturn(List.of(user(10L, "luke", UserRole.RESIDENT)));
        when(topicRepository.findById(1L)).thenReturn(Optional.of(pub));
        when(attachmentService.forTopic(1L)).thenReturn(List.of());
        when(postRepository.findLatestByTopicIds(anyList(), any())).thenReturn(List.of(post(1L, 10L, 1, 0)));

        org.corpsman.forum.dto.PublicPostDto dto = forumService.getLatestVisiblePost(null);

        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getTitle()).isEqualTo("Обсуждение фильма");
        assertThat(dto.getAuthorName()).isEqualTo("luke");
    }

    @Test
    void bestVisiblePostExcludesClosedTopicNotWhitelisted() {
        ForumTopic pub = visibleTopic(1L, 10L, TopicVisibility.PUBLIC);
        ForumTopic closed = visibleTopic(2L, 11L, TopicVisibility.CLOSED);
        when(topicRepository.findAllByDeletedFalse()).thenReturn(List.of(pub, closed));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, "luke", UserRole.RESIDENT)));
        when(settingsService.getByUserId(10L)).thenReturn(settings(true, false));
        when(whitelistRepository.findByTopicIdIn(anyList())).thenReturn(List.of());
        when(userRepository.findByIdIn(anyList())).thenReturn(List.of(user(10L, "luke", UserRole.RESIDENT)));
        when(topicRepository.findById(1L)).thenReturn(Optional.of(pub));
        when(attachmentService.forTopic(1L)).thenReturn(List.of());
        when(postRepository.findBestByTopicIds(anyList(), any())).thenReturn(List.of(post(1L, 10L, 9, 1)));

        org.corpsman.forum.dto.PublicPostDto dto = forumService.getBestVisiblePost(10L);

        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getLikes()).isEqualTo(9);
        assertThat(dto.getDislikes()).isEqualTo(1);
    }

    @Test
    void guestBestVisiblePostDoesNotSeeOpenTopics() {
        ForumTopic open = visibleTopic(1L, 10L, TopicVisibility.OPEN);
        ForumTopic guestTopic = visibleTopic(2L, 10L, TopicVisibility.GUEST);
        when(topicRepository.findAllByDeletedFalse()).thenReturn(List.of(open, guestTopic));
        when(userRepository.findById(20L)).thenReturn(Optional.of(user(20L, "guest1", UserRole.GUEST)));
        when(settingsService.getByUserId(20L)).thenReturn(settings(true, false));
        when(whitelistRepository.findByTopicIdIn(anyList())).thenReturn(List.of());
        when(userRepository.findByIdIn(anyList())).thenReturn(List.of(user(10L, "luke", UserRole.RESIDENT)));
        when(topicRepository.findById(2L)).thenReturn(Optional.of(guestTopic));
        when(attachmentService.forTopic(2L)).thenReturn(List.of());
        when(postRepository.findBestByTopicIds(anyList(), any())).thenReturn(List.of(post(2L, 10L, 3, 0)));

        org.corpsman.forum.dto.PublicPostDto dto = forumService.getBestVisiblePost(20L);

        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(2L);
    }

    @Test
    void latestVisiblePostHidesPublicWhenSettingDisabled() {
        ForumTopic pub = visibleTopic(1L, 10L, TopicVisibility.PUBLIC);
        when(topicRepository.findAllByDeletedFalse()).thenReturn(List.of(pub));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, "luke", UserRole.RESIDENT)));
        when(settingsService.getByUserId(10L)).thenReturn(settings(false, false));
        when(whitelistRepository.findByTopicIdIn(anyList())).thenReturn(List.of());

        org.corpsman.forum.dto.PublicPostDto dto = forumService.getLatestVisiblePost(10L);

        assertThat(dto).isNull();
    }
}