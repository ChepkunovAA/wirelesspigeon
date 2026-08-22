package org.corpsman.forum.service;

import org.corpsman.auth.model.User;
import org.corpsman.auth.model.UserRole;
import org.corpsman.auth.repository.UserRepository;
import org.corpsman.common.event.TopicCreatedEvent;
import org.corpsman.common.exception.BusinessException;
import org.corpsman.common.exception.ErrorCode;
import org.corpsman.forum.dto.TopicCreateRequest;
import org.corpsman.forum.dto.TopicDetailDto;
import org.corpsman.forum.dto.TopicSummaryDto;
import org.corpsman.forum.model.ForumPost;
import org.corpsman.forum.model.ForumTag;
import org.corpsman.forum.model.ForumTopic;
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
    void getTopicsMapsSummaryWithCountsAndAnonymousAuthor() {
        ForumTopic visible = topic(1L, 10L, true, false);
        visible.setTags(Set.of());
        ForumTag tag = new ForumTag();
        tag.setName("важное");
        visible.setTags(Set.of(tag));
        when(topicRepository.search(any(), anyBoolean(), any(), anyBoolean(), anyBoolean(), anyList(), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(visible), PageRequest.of(0, 20), 1));
        when(postRepository.countByTopicIdIn(List.of(1L))).thenReturn(List.<Object[]>of(new Object[]{1L, 5L}));
        when(userRepository.findByIdIn(anyList())).thenReturn(List.of(user(10L, "luke", UserRole.USER)));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, "luke", UserRole.USER)));

        Page<TopicSummaryDto> result = forumService.getTopics(PageRequest.of(0, 20), "фильм", null, false, 10L);

        assertThat(result.getContent()).hasSize(1);
        TopicSummaryDto dto = result.getContent().get(0);
        assertThat(dto.getTitle()).isEqualTo("Обсуждение фильма");
        assertThat(dto.getPostsCount()).isEqualTo(5L);
        assertThat(dto.getAuthorName()).isEqualTo("Аноним");
        assertThat(dto.getTags()).containsExactly("важное");
        assertThat(dto.isAuthor()).isTrue();
    }

    @Test
    void getTopicBuildsTreeAndDetectsModerator() {
        ForumTopic topic = topic(1L, 10L, false, false);
        when(topicRepository.findById(1L)).thenReturn(Optional.of(topic));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user(10L, "luke", UserRole.USER)));
        when(userRepository.findByIdIn(anyList())).thenReturn(List.of(user(10L, "luke", UserRole.USER)));

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
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "admin", UserRole.ADMIN)));
        when(userRepository.findByIdIn(anyList())).thenReturn(List.of(user(10L, "luke", UserRole.USER)));
        when(postRepository.findByTopicIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());
        when(banRepository.findByTopicIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        TopicDetailDto dto = forumService.getTopic(1L, 1L, PageRequest.of(0, 20));

        assertThat(dto.getFirstPost().getRealAuthorName()).isEqualTo("luke");
    }

    @Test
    void realAuthorNotShownForNonAnonymousTopic() {
        ForumTopic topic = topic(1L, 10L, false, false);
        when(topicRepository.findById(1L)).thenReturn(Optional.of(topic));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "admin", UserRole.ADMIN)));
        when(userRepository.findByIdIn(anyList())).thenReturn(List.of(user(10L, "luke", UserRole.USER)));
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
}