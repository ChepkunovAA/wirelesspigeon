package org.corpsman.forum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.corpsman.auth.repository.UserRepository;
import org.corpsman.common.event.PostCreatedEvent;
import org.corpsman.common.exception.BusinessException;
import org.corpsman.common.exception.ErrorCode;
import org.corpsman.forum.dto.ForumPostRequest;
import org.corpsman.forum.dto.LikeResponse;
import org.corpsman.forum.model.ForumPost;
import org.corpsman.forum.model.ForumTopic;
import org.corpsman.forum.model.PostDislike;
import org.corpsman.forum.model.PostLike;
import org.corpsman.forum.repository.ForumPostRepository;
import org.corpsman.forum.repository.ForumTopicRepository;
import org.corpsman.forum.repository.PostDislikeRepository;
import org.corpsman.forum.repository.PostLikeRepository;
import org.corpsman.forum.repository.TopicBanRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Сервис постов форума: создание, редактирование, удаление, лайки.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PostService {

    private static final String DELETED_CONTENT = "[Удалено]";

    private final ForumTopicRepository topicRepository;
    private final ForumPostRepository postRepository;
    private final TopicBanRepository banRepository;
    private final PostLikeRepository likeRepository;
    private final PostDislikeRepository dislikeRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AttachmentService attachmentService;

    /**
     * Создаёт комментарий в треде.
     *
     * @param request данные комментария
     * @param userId  автор
     * @param files   прикрепляемые файлы (могут быть пустыми)
     * @return созданный пост
     */
    @Transactional
    public ForumPost createPost(ForumPostRequest request, Long userId, List<MultipartFile> files) {
        ForumTopic topic = findTopic(request.getTopicId());
        if (topic.isDeleted()) {
            throw new BusinessException(ErrorCode.TOPIC_NOT_FOUND);
        }
        if (topic.isFrozen()) {
            throw new BusinessException(ErrorCode.TOPIC_FROZEN);
        }
        if (banRepository.existsByTopicIdAndUserId(topic.getId(), userId)) {
            throw new BusinessException(ErrorCode.USER_BANNED);
        }
        if (request.getParentPostId() != null) {
            ForumPost parent = postRepository.findById(request.getParentPostId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
            if (!parent.getTopicId().equals(topic.getId())) {
                throw new BusinessException(ErrorCode.POST_NOT_FOUND);
            }
        }
        if (request.getReplyToId() != null) {
            ForumPost target = postRepository.findById(request.getReplyToId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
            if (!target.getTopicId().equals(topic.getId())) {
                throw new BusinessException(ErrorCode.POST_NOT_FOUND);
            }
        }
        ForumPost post = new ForumPost();
        post.setTopicId(topic.getId());
        post.setParentPostId(request.getParentPostId());
        post.setReplyToId(request.getReplyToId());
        post.setContent(request.getContent().trim());
        post.setAuthorId(userId);
        post.setAnonymous(request.isAnonymous());
        post.setEdited(false);
        post.setLikes(0);
        post.setDeleted(false);
        ForumPost saved = postRepository.save(post);
        topic.setLastActivityAt(LocalDateTime.now());
        topicRepository.save(topic);
        attachmentService.attachToPost(files, saved.getId());
        log.info("Создан комментарий (ID: {}) в треде {} пользователем {}", saved.getId(), topic.getId(), userId);
        eventPublisher.publishEvent(new PostCreatedEvent(
                saved.getId(), topic.getId(), saved.isAnonymous(), userId, LocalDateTime.now()));
        return saved;
    }

    /**
     * Редактирует свой комментарий.
     *
     * @param postId     идентификатор поста
     * @param newContent новое содержание
     * @param userId     текущий пользователь
     * @param isAdmin    признак администратора
     */
    @Transactional
    public void editPost(Long postId, String newContent, Long userId, boolean isAdmin) {
        ForumPost post = findPost(postId);
        if (!post.getAuthorId().equals(userId) && !isAdmin) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }
        post.setContent(newContent.trim());
        post.setEdited(true);
        postRepository.save(post);
        log.info("Изменён комментарий (ID: {}) пользователем {}", postId, userId);
    }

    /**
     * Удаляет комментарий (автор, модератор треда или админ). Ветка ответов также удаляется.
     *
     * @param postId  идентификатор поста
     * @param userId  текущий пользователь
     * @param isAdmin признак администратора
     */
    @Transactional
    public void deletePost(Long postId, Long userId, boolean isAdmin) {
        ForumPost post = findPost(postId);
        ForumTopic topic = findTopic(post.getTopicId());
        boolean isModerator = topic.getAuthorId() != null && topic.getAuthorId().equals(userId);
        if (!post.getAuthorId().equals(userId) && !isModerator && !isAdmin) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }
        markDeletedDeep(postId, post.getTopicId());
        log.info("Комментарий (ID: {}) удалён пользователем {}", postId, userId);
    }

    /**
     * Возвращает пост по идентификатору.
     *
     * @param postId идентификатор поста
     * @return пост
     */
    @Transactional(readOnly = true)
    public ForumPost getPost(Long postId) {
        return findPost(postId);
    }

    /**
     * Лайк поста (идемпотентный). Свой пост лайкать нельзя. Дизлайк при этом снимается.
     *
     * @param postId идентификатор поста
     * @param userId текущий пользователь
     * @return новое состояние
     */
    @Transactional
    public LikeResponse likePost(Long postId, Long userId) {
        ForumPost post = findPost(postId);
        if (post.getAuthorId().equals(userId)) {
            throw new BusinessException(ErrorCode.CANNOT_LIKE_OWN_POST);
        }
        if (likeRepository.existsByPostIdAndUserId(postId, userId)) {
            boolean disliked = dislikeRepository.existsByPostIdAndUserId(postId, userId);
            return new LikeResponse(post.getLikes(), true, post.getDislikes(), disliked);
        }
        if (dislikeRepository.existsByPostIdAndUserId(postId, userId)) {
            dislikeRepository.deleteByPostIdAndUserId(postId, userId);
            postRepository.decrementDislikes(postId);
        }
        postRepository.incrementLikes(postId);
        PostLike like = new PostLike();
        like.setPostId(postId);
        like.setUserId(userId);
        likeRepository.save(like);
        return new LikeResponse(post.getLikes() + 1, true, Math.max(0, post.getDislikes() - 1), false);
    }

    /**
     * Снимает лайк (идемпотентный).
     *
     * @param postId идентификатор поста
     * @param userId текущий пользователь
     * @return новое состояние
     */
    @Transactional
    public LikeResponse unlikePost(Long postId, Long userId) {
        ForumPost post = findPost(postId);
        boolean disliked = dislikeRepository.existsByPostIdAndUserId(postId, userId);
        if (likeRepository.findByPostIdAndUserId(postId, userId).isPresent()) {
            likeRepository.deleteByPostIdAndUserId(postId, userId);
            postRepository.decrementLikes(postId);
            return new LikeResponse(Math.max(0, post.getLikes() - 1), false, post.getDislikes(), disliked);
        }
        return new LikeResponse(post.getLikes(), false, post.getDislikes(), disliked);
    }

    /**
     * Дизлайк поста (идемпотентный). Свой пост дизлайкать нельзя. Лайк при этом снимается.
     *
     * @param postId идентификатор поста
     * @param userId текущий пользователь
     * @return новое состояние
     */
    @Transactional
    public LikeResponse dislikePost(Long postId, Long userId) {
        ForumPost post = findPost(postId);
        if (post.getAuthorId().equals(userId)) {
            throw new BusinessException(ErrorCode.CANNOT_DISLIKE_OWN_POST);
        }
        if (dislikeRepository.existsByPostIdAndUserId(postId, userId)) {
            boolean liked = likeRepository.existsByPostIdAndUserId(postId, userId);
            return new LikeResponse(post.getLikes(), liked, post.getDislikes(), true);
        }
        if (likeRepository.existsByPostIdAndUserId(postId, userId)) {
            likeRepository.deleteByPostIdAndUserId(postId, userId);
            postRepository.decrementLikes(postId);
        }
        postRepository.incrementDislikes(postId);
        PostDislike dislike = new PostDislike();
        dislike.setPostId(postId);
        dislike.setUserId(userId);
        dislikeRepository.save(dislike);
        return new LikeResponse(Math.max(0, post.getLikes() - 1), false, post.getDislikes() + 1, true);
    }

    /**
     * Снимает дизлайк (идемпотентный).
     *
     * @param postId идентификатор поста
     * @param userId текущий пользователь
     * @return новое состояние
     */
    @Transactional
    public LikeResponse undislikePost(Long postId, Long userId) {
        ForumPost post = findPost(postId);
        boolean liked = likeRepository.existsByPostIdAndUserId(postId, userId);
        if (dislikeRepository.findByPostIdAndUserId(postId, userId).isPresent()) {
            dislikeRepository.deleteByPostIdAndUserId(postId, userId);
            postRepository.decrementDislikes(postId);
            return new LikeResponse(post.getLikes(), liked, Math.max(0, post.getDislikes() - 1), false);
        }
        return new LikeResponse(post.getLikes(), liked, post.getDislikes(), false);
    }

    /**
     * Проверяет, что текущий пользователь — админ.
     *
     * @param userId идентификатор пользователя
     * @return признак админа
     */
    public boolean isAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        return userRepository.findById(userId)
                .map(user -> user.getRole() == org.corpsman.auth.model.UserRole.ADMIN)
                .orElse(false);
    }

    private void markDeletedDeep(Long postId, Long topicId) {
        ForumPost post = findPost(postId);
        post.setContent(DELETED_CONTENT);
        post.setDeleted(true);
        postRepository.save(post);
        List<ForumPost> children = postRepository.findByTopicIdOrderByCreatedAtAsc(topicId).stream()
                .filter(p -> postId.equals(p.getParentPostId()))
                .toList();
        for (ForumPost child : children) {
            markDeletedDeep(child.getId(), topicId);
        }
    }

    private ForumPost findPost(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
    }

    private ForumTopic findTopic(Long topicId) {
        return topicRepository.findById(topicId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TOPIC_NOT_FOUND));
    }
}