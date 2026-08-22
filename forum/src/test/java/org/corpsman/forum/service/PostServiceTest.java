package org.corpsman.forum.service;

import org.corpsman.auth.model.User;
import org.corpsman.auth.model.UserRole;
import org.corpsman.auth.repository.UserRepository;
import org.corpsman.common.event.PostCreatedEvent;
import org.corpsman.common.exception.BusinessException;
import org.corpsman.common.exception.ErrorCode;
import org.corpsman.forum.dto.ForumPostRequest;
import org.corpsman.forum.dto.LikeResponse;
import org.corpsman.forum.model.ForumPost;
import org.corpsman.forum.model.ForumTopic;
import org.corpsman.forum.repository.ForumPostRepository;
import org.corpsman.forum.repository.ForumTopicRepository;
import org.corpsman.forum.repository.PostDislikeRepository;
import org.corpsman.forum.repository.PostLikeRepository;
import org.corpsman.forum.repository.TopicBanRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private ForumTopicRepository topicRepository;
    @Mock
    private ForumPostRepository postRepository;
    @Mock
    private TopicBanRepository banRepository;
    @Mock
    private PostLikeRepository likeRepository;
    @Mock
    private PostDislikeRepository dislikeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private org.corpsman.forum.repository.TopicWhitelistRepository whitelistRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private AttachmentService attachmentService;

    @InjectMocks
    private PostService postService;

    private ForumTopic topic(long id, boolean frozen, boolean deleted) {
        ForumTopic topic = new ForumTopic();
        topic.setId(id);
        topic.setTitle("Тред");
        topic.setContent("Содержимое");
        topic.setAuthorId(10L);
        topic.setFrozen(frozen);
        topic.setDeleted(deleted);
        topic.setLastActivityAt(LocalDateTime.now());
        return topic;
    }

    private ForumPost post(long id, long topicId, long authorId, LocalDateTime createdAt) {
        ForumPost post = new ForumPost();
        post.setId(id);
        post.setTopicId(topicId);
        post.setContent("Комментарий");
        post.setAuthorId(authorId);
        post.setAnonymous(false);
        post.setLikes(0);
        post.setDeleted(false);
        post.setEdited(false);
        post.setCreatedAt(createdAt);
        return post;
    }

    private ForumPostRequest request(long topicId, Long parentId, String content) {
        ForumPostRequest request = new ForumPostRequest();
        request.setTopicId(topicId);
        request.setParentPostId(parentId);
        request.setContent(content);
        request.setAnonymous(false);
        return request;
    }

    @Test
    void createPostInFrozenTopicFails() {
        when(topicRepository.findById(1L)).thenReturn(Optional.of(topic(1L, true, false)));

        assertThatThrownBy(() -> postService.createPost(request(1L, null, "текст"), 2L, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TOPIC_FROZEN);
    }

    @Test
    void createPostByBannedUserFails() {
        when(topicRepository.findById(1L)).thenReturn(Optional.of(topic(1L, false, false)));
        when(banRepository.existsByTopicIdAndUserId(1L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> postService.createPost(request(1L, null, "текст"), 2L, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_BANNED);
    }

    @Test
    void createPostSavesAndPublishesEvent() {
        when(topicRepository.findById(1L)).thenReturn(Optional.of(topic(1L, false, false)));
        when(banRepository.existsByTopicIdAndUserId(1L, 2L)).thenReturn(false);
        when(postRepository.save(any(ForumPost.class))).thenAnswer(invocation -> {
            ForumPost saved = invocation.getArgument(0);
            saved.setId(9L);
            return saved;
        });

        ForumPost result = postService.createPost(request(1L, null, "Мой комментарий"), 2L, null);

        ArgumentCaptor<ForumPost> captor = ArgumentCaptor.forClass(ForumPost.class);
        verify(postRepository).save(captor.capture());
        assertThat(captor.getValue().getTopicId()).isEqualTo(1L);
        assertThat(captor.getValue().getAuthorId()).isEqualTo(2L);
        assertThat(result.getId()).isEqualTo(9L);
        verify(eventPublisher).publishEvent(any(PostCreatedEvent.class));
    }

    @Test
    void editPostByForeignUserFails() {
        ForumPost post = post(1L, 1L, 2L, LocalDateTime.now());
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.editPost(1L, "новый текст", 3L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PERMISSION_DENIED);
    }

    @Test
    void editOwnOldPostSucceeds() {
        ForumPost post = post(1L, 1L, 2L, LocalDateTime.now().minusHours(1));
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        postService.editPost(1L, "новый текст", 2L);

        assertThat(post.getContent()).isEqualTo("новый текст");
        assertThat(post.isEdited()).isTrue();
        verify(postRepository).save(post);
    }

    @Test
    void editOwnPostWithinWindowSucceeds() {
        ForumPost post = post(1L, 1L, 2L, LocalDateTime.now());
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        postService.editPost(1L, "новый текст", 2L);

        assertThat(post.getContent()).isEqualTo("новый текст");
        assertThat(post.isEdited()).isTrue();
        verify(postRepository).save(post);
    }

    @Test
    void likeOwnPostFails() {
        ForumPost post = post(1L, 1L, 2L, LocalDateTime.now());
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(topicRepository.findById(post.getTopicId())).thenReturn(Optional.of(openTopic(post.getTopicId())));

        assertThatThrownBy(() -> postService.likePost(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CANNOT_LIKE_OWN_POST);
    }

    @Test
    void likeThenUnlikeUpdatesCount() {
        ForumPost post = post(1L, 1L, 2L, LocalDateTime.now());
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(topicRepository.findById(post.getTopicId())).thenReturn(Optional.of(openTopic(post.getTopicId())));
        when(likeRepository.existsByPostIdAndUserId(1L, 3L)).thenReturn(false);
        when(likeRepository.findByPostIdAndUserId(1L, 3L)).thenReturn(Optional.empty());

        LikeResponse liked = postService.likePost(1L, 3L);
        assertThat(liked.getLikes()).isEqualTo(1);
        assertThat(liked.isLiked()).isTrue();
        verify(postRepository).incrementLikes(1L);
        verify(likeRepository).save(any());

        when(likeRepository.findByPostIdAndUserId(1L, 3L)).thenReturn(Optional.of(
                new org.corpsman.forum.model.PostLike()));
        LikeResponse unliked = postService.unlikePost(1L, 3L);
        assertThat(unliked.getLikes()).isZero();
        assertThat(unliked.isLiked()).isFalse();
        verify(postRepository).decrementLikes(1L);
    }

    @Test
    void deletePostByModeratorSucceeds() {
        ForumTopic topic = topic(1L, false, false);
        topic.setAuthorId(10L);
        when(topicRepository.findById(1L)).thenReturn(Optional.of(topic));
        ForumPost post = post(1L, 1L, 2L, LocalDateTime.now());
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(postRepository.findByTopicIdOrderByCreatedAtAsc(1L)).thenReturn(java.util.List.of());

        postService.deletePost(1L, 10L, false);

        assertThat(post.isDeleted()).isTrue();
        assertThat(post.getContent()).isEqualTo("[Удалено]");
        verify(postRepository).save(post);
    }

    @Test
    void dislikeOwnPostFails() {
        ForumPost post = post(1L, 1L, 2L, LocalDateTime.now());
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(topicRepository.findById(post.getTopicId())).thenReturn(Optional.of(openTopic(post.getTopicId())));

        assertThatThrownBy(() -> postService.dislikePost(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CANNOT_DISLIKE_OWN_POST);
    }

    @Test
    void dislikeThenUndislikeUpdatesCount() {
        ForumPost post = post(1L, 1L, 2L, LocalDateTime.now());
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(topicRepository.findById(post.getTopicId())).thenReturn(Optional.of(openTopic(post.getTopicId())));
        when(dislikeRepository.existsByPostIdAndUserId(1L, 3L)).thenReturn(false);
        when(dislikeRepository.findByPostIdAndUserId(1L, 3L)).thenReturn(Optional.empty());

        LikeResponse disliked = postService.dislikePost(1L, 3L);
        assertThat(disliked.getDislikes()).isEqualTo(1);
        assertThat(disliked.isDisliked()).isTrue();
        verify(postRepository).incrementDislikes(1L);
        verify(dislikeRepository).save(any());

        when(dislikeRepository.findByPostIdAndUserId(1L, 3L)).thenReturn(Optional.of(
                new org.corpsman.forum.model.PostDislike()));
        LikeResponse undisliked = postService.undislikePost(1L, 3L);
        assertThat(undisliked.getDislikes()).isZero();
        assertThat(undisliked.isDisliked()).isFalse();
        verify(postRepository).decrementDislikes(1L);
    }

    @Test
    void likeRemovesDislike() {
        ForumPost post = post(1L, 1L, 2L, LocalDateTime.now());
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(topicRepository.findById(post.getTopicId())).thenReturn(Optional.of(openTopic(post.getTopicId())));
        when(likeRepository.existsByPostIdAndUserId(1L, 3L)).thenReturn(false);
        when(dislikeRepository.existsByPostIdAndUserId(1L, 3L)).thenReturn(true);

        LikeResponse liked = postService.likePost(1L, 3L);

        verify(dislikeRepository).deleteByPostIdAndUserId(1L, 3L);
        verify(postRepository).decrementDislikes(1L);
        verify(postRepository).incrementLikes(1L);
        assertThat(liked.isLiked()).isTrue();
        assertThat(liked.getDislikes()).isZero();
    }

    @Test
    void dislikeRemovesLike() {
        ForumPost post = post(1L, 1L, 2L, LocalDateTime.now());
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(topicRepository.findById(post.getTopicId())).thenReturn(Optional.of(openTopic(post.getTopicId())));
        when(dislikeRepository.existsByPostIdAndUserId(1L, 3L)).thenReturn(false);
        when(likeRepository.existsByPostIdAndUserId(1L, 3L)).thenReturn(true);

        LikeResponse disliked = postService.dislikePost(1L, 3L);

        verify(likeRepository).deleteByPostIdAndUserId(1L, 3L);
        verify(postRepository).decrementLikes(1L);
        verify(postRepository).incrementDislikes(1L);
        assertThat(disliked.isDisliked()).isTrue();
        assertThat(disliked.getLikes()).isZero();
    }

    private ForumTopic openTopic(Long id) {
        ForumTopic topic = new ForumTopic();
        topic.setId(id);
        topic.setVisibility(org.corpsman.forum.model.TopicVisibility.OPEN);
        return topic;
    }
}
