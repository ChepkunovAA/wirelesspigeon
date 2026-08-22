package org.corpsman.forum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.corpsman.auth.model.User;
import org.corpsman.auth.repository.UserRepository;
import org.corpsman.common.event.TopicCreatedEvent;
import org.corpsman.common.exception.BusinessException;
import org.corpsman.common.exception.ErrorCode;
import org.corpsman.forum.dto.ForumPostDto;
import org.corpsman.forum.dto.LikeResponse;
import org.corpsman.forum.dto.PublicPostDto;
import org.corpsman.forum.dto.TopicBanDto;
import org.corpsman.forum.dto.TopicCreateRequest;
import org.corpsman.forum.dto.TopicDetailDto;
import org.corpsman.forum.dto.TopicSummaryDto;
import org.corpsman.forum.model.ForumPost;
import org.corpsman.forum.model.ForumTag;
import org.corpsman.forum.model.TopicVisibility;
import org.corpsman.forum.model.TopicWhitelistEntry;
import org.corpsman.forum.repository.TopicWhitelistRepository;
import org.corpsman.forum.model.ForumTopic;
import org.corpsman.forum.model.PostDislike;
import org.corpsman.forum.model.PostLike;
import org.corpsman.forum.model.TopicBan;
import org.corpsman.forum.model.TopicDislike;
import org.corpsman.forum.model.TopicLike;
import org.corpsman.forum.repository.ForumPostRepository;
import org.corpsman.forum.repository.ForumTagRepository;
import org.corpsman.forum.repository.ForumTopicRepository;
import org.corpsman.forum.repository.PostDislikeRepository;
import org.corpsman.forum.repository.PostLikeRepository;
import org.corpsman.forum.repository.TopicBanRepository;
import org.corpsman.forum.repository.TopicDislikeRepository;
import org.corpsman.forum.repository.TopicLikeRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Сервис форума: треды, теги, заморозка, баны, модерация.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ForumService {

    private static final String ANONYMOUS = "Аноним";
    private static final int PREVIEW_LENGTH = 100;
    private static final int MAX_TREE_DEPTH = 5;

    private final ForumTopicRepository topicRepository;
    private final ForumPostRepository postRepository;
    private final ForumTagRepository tagRepository;
    private final TopicBanRepository banRepository;
    private final PostLikeRepository likeRepository;
    private final PostDislikeRepository dislikeRepository;
    private final TopicLikeRepository topicLikeRepository;
    private final TopicDislikeRepository topicDislikeRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AttachmentService attachmentService;
    private final TopicWhitelistRepository whitelistRepository;

    /**
     * Возвращает последний опубликованный публичный тред для главной страницы.
     *
     * @return публичный тред или null, если таких нет
     */
    @Transactional(readOnly = true)
    public PublicPostDto getLatestPublicPost() {
        return topicRepository.findFirstByVisibilityAndDeletedFalseOrderByCreatedAtDesc(TopicVisibility.PUBLIC)
                .map(this::toPublicPostDto)
                .orElse(null);
    }

    private PublicPostDto toPublicPostDto(ForumTopic topic) {
        PublicPostDto dto = new PublicPostDto();
        dto.setId(topic.getId());
        dto.setTitle(topic.getTitle());
        dto.setAnonymous(topic.isAnonymous());
        dto.setFirstPostPreview(preview(topic.getContent()));
        dto.setCreatedAt(topic.getCreatedAt());
        Map<Long, String> usernames = topic.getAuthorId() == null
                ? Map.of()
                : loadUsernames(List.of(topic.getAuthorId()));
        dto.setAuthorName(topic.isAnonymous() ? ANONYMOUS : usernames.getOrDefault(topic.getAuthorId(), ANONYMOUS));
        dto.setAttachments(attachmentService.forTopic(topic.getId()));
        return dto;
    }

    /**
     * Проверяет, что тред публичен и не удалён.
     *
     * @param topicId идентификатор треда
     * @return true, если тред публикуется на главной странице
     */
    @Transactional(readOnly = true)
    public boolean isPublicTopic(Long topicId) {
        return topicRepository.findById(topicId)
                .map(t -> t.getVisibility() == TopicVisibility.PUBLIC && !t.isDeleted())
                .orElse(false);
    }

    /**
     * Возвращает список тредов с поиском и фильтрацией по тегам.
     *
     * @param pageable постраничность
     * @param search   подстрока названия
     * @param tags     фильтр по тегам
     * @param userId   текущий пользователь
     * @return страница кратких представлений
     */
    @Transactional(readOnly = true)
    public Page<TopicSummaryDto> getTopics(Pageable pageable, String search, List<String> tags,
                                               boolean hidePublic, boolean guestRole, Long userId) {
        String normalizedSearch = StringUtils.hasText(search) ? search.trim() : "";
        boolean hasTags = tags != null && !tags.isEmpty();
        List<String> normalizedTags = hasTags
                ? tags.stream().filter(StringUtils::hasText).toList()
                : List.of();
        if (hasTags && normalizedTags.isEmpty()) {
            hasTags = false;
        }
        Page<ForumTopic> page = topicRepository.search(normalizedSearch, hidePublic,
                guestRole, userId, userId != null && isAdmin(userId), hasTags, normalizedTags, pageable);

        Map<Long, Long> counts = countPostsByTopics(page.getContent());
        Map<Long, String> usernames = loadUsernames(page.getContent().stream()
                .map(ForumTopic::getAuthorId)
                .filter(Objects::nonNull)
                .toList());
        boolean isAdmin = userId != null && isAdmin(userId);

        java.util.Map<Long, Boolean> interactMap = new java.util.HashMap<>();
        for (ForumTopic t : page.getContent()) {
            boolean owner = userId != null && t.getAuthorId() != null && t.getAuthorId().equals(userId);
            if (t.getVisibility() == TopicVisibility.CLOSED && !owner) {
                interactMap.put(t.getId(), userId != null
                        && whitelistRepository.existsByTopicIdAndUserId(t.getId(), userId));
            } else {
                interactMap.put(t.getId(), true);
            }
        }
        List<TopicSummaryDto> dtos = page.getContent().stream()
                .map(topic -> {
                    TopicSummaryDto dto = toSummaryDto(topic, counts, usernames, userId, isAdmin);
                    dto.setCanInteract(interactMap.getOrDefault(topic.getId(), true));
                    return dto;
                })
                .toList();
        return new PageImpl<>(dtos, pageable, page.getTotalElements());
    }

    /**
     * Возвращает полное представление треда с деревом комментариев.
     *
     * @param topicId  идентификатор треда
     * @param userId   текущий пользователь
     * @param pageable постраничность корневых комментариев
     * @return полное представление
     */
    @Transactional
    public TopicDetailDto getTopic(Long topicId, Long userId, Pageable pageable) {
        ForumTopic topic = findTopic(topicId);
        if (topic.isDeleted()) {
            throw new BusinessException(ErrorCode.TOPIC_NOT_FOUND);
        }

        boolean isAdmin = userId != null && isAdmin(userId);
        if (!canViewClosed(topic, userId, isAdmin)) {
            throw new BusinessException(ErrorCode.TOPIC_NOT_FOUND);
        }
        boolean canModerate = canModerate(topic, userId, isAdmin);

        List<ForumPost> allPosts = postRepository.findByTopicIdOrderByCreatedAtAsc(topicId);
        Map<Long, List<ForumPost>> byParent = allPosts.stream()
                .filter(p -> p.getParentPostId() != null)
                .collect(Collectors.groupingBy(ForumPost::getParentPostId));
        Map<Long, ForumPost> byId = allPosts.stream()
                .collect(Collectors.toMap(ForumPost::getId, p -> p));
        List<ForumPost> roots = allPosts.stream()
                .filter(p -> p.getParentPostId() == null && !p.isOp())
                .toList();

        ForumPost opPost = postRepository.findByTopicIdAndOpTrue(topicId).orElse(null);

        Page<ForumPost> rootPage = paginate(roots, pageable);
        Set<Long> authorIds = new java.util.HashSet<>();
        allPosts.forEach(post -> authorIds.add(post.getAuthorId()));
        authorIds.add(topic.getAuthorId());
        List<TopicBan> bans = canModerate
                ? banRepository.findByTopicIdOrderByCreatedAtDesc(topicId)
                : List.of();
        bans.forEach(ban -> {
            authorIds.add(ban.getUserId());
            authorIds.add(ban.getBannedBy());
        });
        Map<Long, String> usernames = loadUsernames(new ArrayList<>(authorIds));
        List<ForumPost> collectFrom = new ArrayList<>(rootPage.getContent());
        if (opPost != null) {
            collectFrom.add(opPost);
        }
        List<Long> postIds = collectAllPostIds(collectFrom, byParent);
        Map<Long, List<org.corpsman.forum.dto.ForumAttachmentDto>> attachmentsByPost = attachmentService.byPostIds(postIds);
        Set<Long> likedPostIds = loadLikedPostIds(postIds, userId);
        Set<Long> dislikedPostIds = loadDislikedPostIds(postIds, userId);

        Long lastContinuationId = roots.stream()
                .filter(ForumPost::isContinuation)
                .map(ForumPost::getId)
                .reduce((a, b) -> b)
                .orElse(null);

        List<ForumPostDto> postDtos = rootPage.getContent().stream()
                .map(root -> {
                    ForumPostDto dto = toPostDto(root, byParent, byId, subtreeSizes(byParent),
                            usernames, likedPostIds, dislikedPostIds, attachmentsByPost,
                            userId, isAdmin, canModerate, 0);
                    dto.setLatestContinuation(root.isContinuation()
                            && root.getId().equals(lastContinuationId));
                    return dto;
                })
                .toList();

        boolean topicLiked = userId != null && topicLikeRepository.existsByTopicIdAndUserId(topicId, userId);
        boolean topicDisliked = userId != null && topicDislikeRepository.existsByTopicIdAndUserId(topicId, userId);

        TopicDetailDto dto = new TopicDetailDto();
        dto.setId(topic.getId());
        dto.setTopicAuthorId(topic.getAuthorId());
        dto.setTitle(topic.getTitle());
        dto.setFrozen(topic.isFrozen());
        dto.setDeleted(topic.isDeleted());
        dto.setVisibility(topic.getVisibility() == null ? TopicVisibility.OPEN.name() : topic.getVisibility().name());
        dto.setReadOnly(topic.isReadOnly());
        dto.setAuthor(topic.getAuthorId() != null && topic.getAuthorId().equals(userId));
        dto.setCanModerate(canModerate);
        dto.setAdmin(isAdmin);
        dto.setBanned(userId != null && banRepository.existsByTopicIdAndUserId(topicId, userId));
        dto.setTags(topic.getTags().stream().map(ForumTag::getName).sorted().toList());
        boolean owner = topic.getAuthorId() != null && topic.getAuthorId().equals(userId);
        dto.setReadOnly(topic.isReadOnly());
        dto.setCanWrite(!topic.isReadOnly() || owner);
        ForumPostDto firstPost;
        if (opPost != null) {
            firstPost = toPostDto(opPost, byParent, byId, subtreeSizes(byParent),
                    usernames, likedPostIds, dislikedPostIds, attachmentsByPost,
                    userId, isAdmin, canModerate, 0);
        } else {
            firstPost = buildFirstPostDto(topic, usernames, userId, isAdmin);
        }
        firstPost.setAttachments(attachmentService.forTopic(topicId));
        firstPost.setLikes(topic.getLikes());
        firstPost.setDislikes(topic.getDislikes());
        firstPost.setLikedByCurrentUser(topicLiked);
        firstPost.setDislikedByCurrentUser(topicDisliked);
        dto.setFirstPost(firstPost);
        dto.setPosts(new PageImpl<>(postDtos, pageable, rootPage.getTotalElements()));
        if (canModerate) {
            dto.setBannedUsers(buildBanDtos(bans, usernames));
        }
        return dto;
    }

    /**
     * Создаёт новый тред.
     *
     * @param request данные треда
     * @param userId  автор
     * @param files   прикрепляемые файлы (могут быть пустыми)
     * @return созданный тред
     */
    @Transactional
    public ForumTopic createTopic(TopicCreateRequest request, Long userId, List<MultipartFile> files) {
        ForumTopic topic = new ForumTopic();
        topic.setTitle(request.getTitle().trim());
        topic.setContent(request.getContent().trim());
        topic.setAuthorId(userId);
        topic.setAnonymous(request.isAnonymous());
        TopicVisibility resolved = resolveVisibility(request.getVisibility());
        if (isGuest(userId) && resolved != TopicVisibility.GUEST) {
            resolved = TopicVisibility.GUEST;
        }
        topic.setVisibility(resolved);
        topic.setReadOnly(request.isReadOnly());
        topic.setLastActivityAt(LocalDateTime.now());
        topic.setTags(resolveTags(request.getTags()));
        ForumTopic saved = topicRepository.save(topic);

        ForumPost opPost = new ForumPost();
        opPost.setTopicId(saved.getId());
        opPost.setContent(saved.getContent());
        opPost.setAuthorId(userId);
        opPost.setAnonymous(saved.isAnonymous());
        opPost.setOp(true);
        postRepository.save(opPost);

        saveWhitelist(saved.getId(), request.getWhitelist());

        attachmentService.attachToTopic(files, saved.getId());
        log.info("Создан тред '{}' (ID: {}) пользователем {}", saved.getTitle(), saved.getId(), userId);
        eventPublisher.publishEvent(new TopicCreatedEvent(saved.getId(), saved.isAnonymous(), userId, LocalDateTime.now()));
        return saved;
    }

    /**
     * Редактирует содержание треда (первый пост).
     *
     * @param topicId    идентификатор треда
     * @param newContent новое содержание
     * @param userId     текущий пользователь
     * @param isAdmin    признак администратора
     */
    @Transactional
    public void editTopic(Long topicId, String newContent, Long userId) {
        ForumTopic topic = findTopic(topicId);
        if (topic.isDeleted()) {
            throw new BusinessException(ErrorCode.TOPIC_NOT_FOUND);
        }
        if (!topic.getAuthorId().equals(userId)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }
        topic.setContent(newContent.trim());
        topicRepository.save(topic);
        log.info("Изменено содержание треда {} (ID: {})", topic.getTitle(), topicId);
    }

    /**
     * Замораживает тред (запрет комментариев).
     *
     * @param topicId идентификатор треда
     * @param userId  текущий пользователь
     * @param isAdmin признак администратора
     */
    @Transactional
    public void freezeTopic(Long topicId, Long userId, boolean isAdmin) {
        ForumTopic topic = findTopic(topicId);
        requireModerator(topic, userId, isAdmin);
        topic.setFrozen(true);
        topicRepository.save(topic);
        log.info("Тред {} (ID: {}) заморожен", topic.getTitle(), topicId);
    }

    /**
     * Размораживает тред.
     *
     * @param topicId идентификатор треда
     * @param userId  текущий пользователь
     * @param isAdmin признак администратора
     */
    @Transactional
    public void unfreezeTopic(Long topicId, Long userId, boolean isAdmin) {
        ForumTopic topic = findTopic(topicId);
        requireModerator(topic, userId, isAdmin);
        topic.setFrozen(false);
        topicRepository.save(topic);
        log.info("Тред {} (ID: {}) разморожен", topic.getTitle(), topicId);
    }

    /**
     * Мягко удаляет тред.
     *
     * @param topicId идентификатор треда
     * @param userId  текущий пользователь
     * @param isAdmin признак администратора
     */
    @Transactional
    public void deleteTopic(Long topicId, Long userId, boolean isAdmin) {
        ForumTopic topic = findTopic(topicId);
        requireModerator(topic, userId, isAdmin);
        topic.setDeleted(true);
        topicRepository.save(topic);
        log.info("Тред {} (ID: {}) удалён", topic.getTitle(), topicId);
    }

    /**
     * Возвращает список забаненных пользователей треда (только модератор или админ).
     *
     * @param topicId идентификатор треда
     * @param userId  текущий пользователь
     * @param isAdmin признак администратора
     * @return список банов
     */
    @Transactional(readOnly = true)
    public List<TopicBanDto> getBannedUsers(Long topicId, Long userId, boolean isAdmin) {
        ForumTopic topic = findTopic(topicId);
        requireModerator(topic, userId, isAdmin);
        List<TopicBan> bans = banRepository.findByTopicIdOrderByCreatedAtDesc(topicId);
        Map<Long, String> usernames = loadUsernames(bans.stream()
                .flatMap(ban -> java.util.stream.Stream.of(ban.getUserId(), ban.getBannedBy()))
                .toList());
        return buildBanDtos(bans, usernames);
    }

    /**
     * Банит пользователя в треде.
     *
     * @param topicId     идентификатор треда
     * @param targetId    банимый пользователь
     * @param moderatorId модератор (автор треда или админ)
     * @param isAdmin     признак администратора
     * @param displayName имя, показанное модератору на момент бана ("Аноним" для анонимных авторов)
     */
    @Transactional
    public void banUser(Long topicId, Long targetId, Long moderatorId, boolean isAdmin, String displayName) {
        ForumTopic topic = findTopic(topicId);
        requireModerator(topic, moderatorId, isAdmin);
        if (topic.getAuthorId().equals(targetId)) {
            throw new BusinessException(ErrorCode.CANNOT_BAN_AUTHOR);
        }
        if (targetId.equals(moderatorId)) {
            throw new BusinessException(ErrorCode.CANNOT_BAN_AUTHOR);
        }
        if (banRepository.existsByTopicIdAndUserId(topicId, targetId)) {
            throw new BusinessException(ErrorCode.ALREADY_BANNED);
        }
        TopicBan ban = new TopicBan();
        ban.setTopicId(topicId);
        ban.setUserId(targetId);
        ban.setBannedBy(moderatorId);
        ban.setDisplayName(displayName);
        banRepository.save(ban);
        log.info("Пользователь {} забанен в треде {} модератором {}", targetId, topicId, moderatorId);
    }

    /**
     * Лайк первого поста треда (идемпотентный). Свой тред лайкать нельзя.
     *
     * @param topicId идентификатор треда
     * @param userId  текущий пользователь
     * @return новое состояние
     */
    @Transactional
    public LikeResponse likeTopic(Long topicId, Long userId) {
        requireClosedInteraction(topicId, userId);
        ForumTopic topic = findTopic(topicId);
        if (topic.getAuthorId().equals(userId)) {
            throw new BusinessException(ErrorCode.CANNOT_LIKE_OWN_POST);
        }
        if (topicLikeRepository.existsByTopicIdAndUserId(topicId, userId)) {
            boolean disliked = topicDislikeRepository.existsByTopicIdAndUserId(topicId, userId);
            return new LikeResponse(topic.getLikes(), true, topic.getDislikes(), disliked);
        }
        if (topicDislikeRepository.existsByTopicIdAndUserId(topicId, userId)) {
            topicDislikeRepository.deleteByTopicIdAndUserId(topicId, userId);
            topicRepository.decrementDislikes(topicId);
        }
        topicRepository.incrementLikes(topicId);
        TopicLike like = new TopicLike();
        like.setTopicId(topicId);
        like.setUserId(userId);
        topicLikeRepository.save(like);
        return new LikeResponse(topic.getLikes() + 1, true, Math.max(0, topic.getDislikes() - 1), false);
    }

    /**
     * Снимает лайк с первого поста треда (идемпотентный).
     *
     * @param topicId идентификатор треда
     * @param userId  текущий пользователь
     * @return новое состояние
     */
    @Transactional
    public LikeResponse unlikeTopic(Long topicId, Long userId) {
        requireClosedInteraction(topicId, userId);
        ForumTopic topic = findTopic(topicId);
        boolean disliked = topicDislikeRepository.existsByTopicIdAndUserId(topicId, userId);
        if (topicLikeRepository.findByTopicIdAndUserId(topicId, userId).isPresent()) {
            topicLikeRepository.deleteByTopicIdAndUserId(topicId, userId);
            topicRepository.decrementLikes(topicId);
            return new LikeResponse(Math.max(0, topic.getLikes() - 1), false, topic.getDislikes(), disliked);
        }
        return new LikeResponse(topic.getLikes(), false, topic.getDislikes(), disliked);
    }

    /**
     * Дизлайк первого поста треда (идемпотентный). Свой тред дизлайкать нельзя.
     *
     * @param topicId идентификатор треда
     * @param userId  текущий пользователь
     * @return новое состояние
     */
    @Transactional
    public LikeResponse dislikeTopic(Long topicId, Long userId) {
        requireClosedInteraction(topicId, userId);
        ForumTopic topic = findTopic(topicId);
        if (topic.getAuthorId().equals(userId)) {
            throw new BusinessException(ErrorCode.CANNOT_DISLIKE_OWN_POST);
        }
        if (topicDislikeRepository.existsByTopicIdAndUserId(topicId, userId)) {
            boolean liked = topicLikeRepository.existsByTopicIdAndUserId(topicId, userId);
            return new LikeResponse(topic.getLikes(), liked, topic.getDislikes(), true);
        }
        if (topicLikeRepository.existsByTopicIdAndUserId(topicId, userId)) {
            topicLikeRepository.deleteByTopicIdAndUserId(topicId, userId);
            topicRepository.decrementLikes(topicId);
        }
        topicRepository.incrementDislikes(topicId);
        TopicDislike dislike = new TopicDislike();
        dislike.setTopicId(topicId);
        dislike.setUserId(userId);
        topicDislikeRepository.save(dislike);
        return new LikeResponse(Math.max(0, topic.getLikes() - 1), false, topic.getDislikes() + 1, true);
    }

    /**
     * Снимает дизлайк с первого поста треда (идемпотентный).
     *
     * @param topicId идентификатор треда
     * @param userId  текущий пользователь
     * @return новое состояние
     */
    @Transactional
    public LikeResponse undislikeTopic(Long topicId, Long userId) {
        requireClosedInteraction(topicId, userId);
        ForumTopic topic = findTopic(topicId);
        boolean liked = topicLikeRepository.existsByTopicIdAndUserId(topicId, userId);
        if (topicDislikeRepository.findByTopicIdAndUserId(topicId, userId).isPresent()) {
            topicDislikeRepository.deleteByTopicIdAndUserId(topicId, userId);
            topicRepository.decrementDislikes(topicId);
            return new LikeResponse(topic.getLikes(), liked, Math.max(0, topic.getDislikes() - 1), false);
        }
        return new LikeResponse(topic.getLikes(), liked, topic.getDislikes(), false);
    }

    /**
     * Разбанивает пользователя в треде.
     *
     * @param topicId    идентификатор треда
     * @param targetId   разбаниваемый пользователь
     * @param moderatorId модератор
     * @param isAdmin    признак администратора
     */
    @Transactional
    public void unbanUser(Long topicId, Long targetId, Long moderatorId, boolean isAdmin) {
        ForumTopic topic = findTopic(topicId);
        requireModerator(topic, moderatorId, isAdmin);
        if (!banRepository.existsByTopicIdAndUserId(topicId, targetId)) {
            throw new BusinessException(ErrorCode.USER_NOT_BANNED);
        }
        banRepository.deleteByTopicIdAndUserId(topicId, targetId);
        log.info("Пользователь {} разбанен в треде {} модератором {}", targetId, topicId, moderatorId);
    }

    /**
     * Возвращает популярные теги с количеством тредов.
     *
     * @return список [имя, количество]
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getPopularTags() {
        return tagRepository.findPopularTags().stream()
                .map(row -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("name", row[0]);
                    entry.put("count", row[1]);
                    return entry;
                })
                .toList();
    }

    /**
     * Возвращает все теги.
     *
     * @return список имён тегов
     */
    @Transactional(readOnly = true)
    public List<String> getAllTags() {
        return tagRepository.findAllByOrderByNameAsc().stream()
                .map(ForumTag::getName)
                .toList();
    }

    private TopicSummaryDto toSummaryDto(ForumTopic topic, Map<Long, Long> counts,
                                         Map<Long, String> usernames, Long userId, boolean isAdmin) {
        TopicSummaryDto dto = new TopicSummaryDto();
        dto.setId(topic.getId());
        dto.setAuthorId(topic.getAuthorId());
        dto.setTitle(topic.getTitle());
        dto.setAuthorName(topic.isAnonymous() ? ANONYMOUS : usernames.getOrDefault(topic.getAuthorId(), ANONYMOUS));
        dto.setAnonymous(topic.isAnonymous());
        dto.setFirstPostPreview(preview(topic.getContent()));
        dto.setCreatedAt(topic.getCreatedAt());
        dto.setLastActivityAt(topic.getLastActivityAt());
        dto.setPostsCount(counts.getOrDefault(topic.getId(), 0L));
        dto.setLikes(topic.getLikes());
        dto.setDislikes(topic.getDislikes());
        dto.setFrozen(topic.isFrozen());
        dto.setDeleted(topic.isDeleted());
        dto.setVisibility(topic.getVisibility() == null ? TopicVisibility.OPEN.name() : topic.getVisibility().name());
        dto.setReadOnly(topic.isReadOnly());
        dto.setTags(topic.getTags().stream().map(ForumTag::getName).sorted().toList());
        dto.setAuthorName(topic.isAnonymous() ? ANONYMOUS : usernames.getOrDefault(topic.getAuthorId(), ANONYMOUS));
        dto.setAuthor(topic.getAuthorId() != null && topic.getAuthorId().equals(userId));
        if (isAdmin && topic.isAnonymous()) {
            dto.setRealAuthorName(usernames.get(topic.getAuthorId()));
        }
        return dto;
    }

    private ForumPostDto buildFirstPostDto(ForumTopic topic, Map<Long, String> usernames, Long userId, boolean isAdmin) {
        ForumPostDto dto = new ForumPostDto();
        dto.setId(topic.getId());
        dto.setParentPostId(null);
        dto.setContent(topic.getContent());
        dto.setAuthorName(topic.isAnonymous() ? ANONYMOUS : usernames.getOrDefault(topic.getAuthorId(), ANONYMOUS));
        dto.setAnonymous(topic.isAnonymous());
        dto.setDeleted(topic.isDeleted());
        dto.setCreatedAt(topic.getCreatedAt());
        dto.setEdited(false);
        dto.setOp(true);
        dto.setAuthorId(topic.getAuthorId());
        dto.setAuthor(topic.getAuthorId() != null && topic.getAuthorId().equals(userId));
        dto.setLikes(topic.getLikes());
        dto.setDislikes(topic.getDislikes());
        if (isAdmin && topic.isAnonymous()) {
            dto.setRealAuthorName(usernames.get(topic.getAuthorId()));
        }
        return dto;
    }

    private ForumPostDto toPostDto(ForumPost post, Map<Long, List<ForumPost>> byParent,
                                   Map<Long, ForumPost> byId, Map<Long, Long> subtreeSizes,
                                   Map<Long, String> usernames, Set<Long> likedPostIds,
                                   Set<Long> dislikedPostIds,
                                   Map<Long, List<org.corpsman.forum.dto.ForumAttachmentDto>> attachmentsByPost,
                                   Long userId, boolean isAdmin, boolean canModerate, int depth) {
        ForumPostDto dto = new ForumPostDto();
        dto.setId(post.getId());
        dto.setOp(post.isOp());
        dto.setContinuation(post.isContinuation());
        dto.setParentPostId(post.getParentPostId());
        dto.setReplyToId(post.getReplyToId());
        dto.setContent(post.getContent());
        dto.setAnonymous(post.isAnonymous());
        dto.setDeleted(post.isDeleted());
        dto.setDeletedByAdmin(post.isDeletedByAdmin());
        dto.setLikes(post.getLikes());
        dto.setDislikes(post.getDislikes());
        dto.setCreatedAt(post.getCreatedAt());
        dto.setEdited(post.isEdited());
        dto.setAuthorId(post.getAuthorId());
        dto.setAuthorName(post.isAnonymous() ? ANONYMOUS : usernames.getOrDefault(post.getAuthorId(), ANONYMOUS));
        dto.setAuthor(post.getAuthorId() != null && post.getAuthorId().equals(userId));
        dto.setLikedByCurrentUser(likedPostIds.contains(post.getId()));
        dto.setDislikedByCurrentUser(dislikedPostIds.contains(post.getId()));
        dto.setSubtreeSize(subtreeSizes.getOrDefault(post.getId(), 0L));
        dto.setAttachments(attachmentsByPost.getOrDefault(post.getId(), List.of()));
        if (isAdmin && post.isAnonymous()) {
            dto.setRealAuthorName(usernames.get(post.getAuthorId()));
        }
        if (post.getReplyToId() != null) {
            ForumPost target = byId.get(post.getReplyToId());
            if (target != null && !post.getId().equals(target.getId())) {
                dto.setParentContent(target.isDeleted() ? "[Удалено]" : preview(target.getContent()));
                dto.setParentAuthorName(target.isAnonymous()
                        ? ANONYMOUS
                        : usernames.getOrDefault(target.getAuthorId(), ANONYMOUS));
            }
        }
        if (depth < MAX_TREE_DEPTH) {
            List<ForumPost> replies = byParent.getOrDefault(post.getId(), List.of());
            dto.setReplies(replies.stream()
                    .map(reply -> toPostDto(reply, byParent, byId, subtreeSizes, usernames, likedPostIds,
                            dislikedPostIds, attachmentsByPost, userId, isAdmin, canModerate, depth + 1))
                    .toList());
        }
        return dto;
    }

    private List<TopicBanDto> buildBanDtos(List<TopicBan> bans, Map<Long, String> usernames) {
        return bans.stream()
                .map(ban -> {
                    TopicBanDto dto = new TopicBanDto();
                    dto.setUserId(ban.getUserId());
                    String displayName = ban.getDisplayName();
                    dto.setUsername(displayName != null && !displayName.isBlank()
                            ? displayName
                            : usernames.getOrDefault(ban.getUserId(), ANONYMOUS));
                    dto.setBannedBy(usernames.get(ban.getBannedBy()));
                    dto.setReason(ban.getReason());
                    dto.setCreatedAt(ban.getCreatedAt());
                    return dto;
                })
                .toList();
    }

    private Map<Long, Long> subtreeSizes(Map<Long, List<ForumPost>> byParent) {
        Map<Long, Long> sizes = new HashMap<>();
        for (Long parentId : byParent.keySet()) {
            subtreeSize(parentId, byParent, sizes);
        }
        return sizes;
    }

    private long subtreeSize(Long postId, Map<Long, List<ForumPost>> byParent, Map<Long, Long> sizes) {
        Long cached = sizes.get(postId);
        if (cached != null) {
            return cached;
        }
        long size = 0;
        for (ForumPost reply : byParent.getOrDefault(postId, List.of())) {
            size += 1 + subtreeSize(reply.getId(), byParent, sizes);
        }
        sizes.put(postId, size);
        return size;
    }

    private Map<Long, Long> countPostsByTopics(List<ForumTopic> topics) {
        if (topics.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = topics.stream().map(ForumTopic::getId).toList();
        return postRepository.countByTopicIdIn(ids).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }

    private Map<Long, String> loadUsernames(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findByIdIn(userIds.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));
    }

    private Set<Long> loadLikedPostIds(List<Long> postIds, Long userId) {
        if (userId == null || postIds.isEmpty()) {
            return Set.of();
        }
        return likeRepository.findByPostIdInAndUserId(postIds, userId).stream()
                .map(PostLike::getPostId)
                .collect(Collectors.toSet());
    }

    private Set<Long> loadDislikedPostIds(List<Long> postIds, Long userId) {
        if (userId == null || postIds.isEmpty()) {
            return Set.of();
        }
        return dislikeRepository.findByPostIdInAndUserId(postIds, userId).stream()
                .map(PostDislike::getPostId)
                .collect(Collectors.toSet());
    }

    private List<Long> collectAllPostIds(List<ForumPost> roots, Map<Long, List<ForumPost>> byParent) {
        List<Long> ids = new ArrayList<>();
        for (ForumPost root : roots) {
            ids.add(root.getId());
            collectChildrenIds(root.getId(), byParent, ids);
        }
        return ids;
    }

    private void collectChildrenIds(Long parentId, Map<Long, List<ForumPost>> byParent, List<Long> ids) {
        for (ForumPost child : byParent.getOrDefault(parentId, List.of())) {
            ids.add(child.getId());
            collectChildrenIds(child.getId(), byParent, ids);
        }
    }

    private Page<ForumPost> paginate(List<ForumPost> roots, Pageable pageable) {
        int pageNumber = pageable.getPageNumber();
        int pageSize = pageable.getPageSize();
        int from = Math.min(pageNumber * pageSize, roots.size());
        int to = Math.min(from + pageSize, roots.size());
        return new PageImpl<>(roots.subList(from, to), pageable, roots.size());
    }

    private Set<ForumTag> resolveTags(List<String> names) {
        if (names == null || names.isEmpty()) {
            return new java.util.HashSet<>();
        }
        List<String> normalized = names.stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            return new java.util.HashSet<>();
        }
        Map<String, ForumTag> existing = tagRepository.findByNameIn(normalized).stream()
                .collect(Collectors.toMap(ForumTag::getName, Function.identity()));
        java.util.HashSet<ForumTag> result = new java.util.HashSet<>();
        for (String name : normalized) {
            ForumTag tag = existing.get(name);
            if (tag == null) {
                tag = new ForumTag();
                tag.setName(name);
                tag = tagRepository.save(tag);
            }
            result.add(tag);
        }
        return result;
    }

    private boolean canModerate(ForumTopic topic, Long userId, boolean isAdmin) {
        if (isAdmin) {
            // Мастер модеррует любой тред
            return true;
        }
        // Остальные роли — только собственные треды
        return topic.getAuthorId() != null && topic.getAuthorId().equals(userId);
    }

    private void requireModerator(ForumTopic topic, Long userId, boolean isAdmin) {
        if (!canModerate(topic, userId, isAdmin)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }
    }

    private ForumTopic findTopic(Long topicId) {
        return topicRepository.findById(topicId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TOPIC_NOT_FOUND));
    }

    private String preview(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String singleLine = content.replaceAll("\\s+", " ").trim();
        return singleLine.length() <= PREVIEW_LENGTH
                ? singleLine
                : singleLine.substring(0, PREVIEW_LENGTH).trim() + "…";
    }

    private boolean isAdmin(Long userId) {
        return userRepository.findById(userId)
                .map(user -> user.getRole() == org.corpsman.auth.model.UserRole.MASTER)
                .orElse(false);
    }

    /**
     * Нормализует видимость треда из строки формы.
     *
     * @param raw значение из формы (PUBLIC/OPEN/CLOSED)
     * @return видимость; по умолчанию OPEN
     */
    private TopicVisibility resolveVisibility(String raw) {
        if (raw == null) {
            return TopicVisibility.OPEN;
        }
        try {
            return TopicVisibility.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return TopicVisibility.OPEN;
        }
    }

    /**
     * Сохраняет белый список закрытого треда по никам через запятую.
     *
     * @param topicId идентификатор треда
     * @param csv     ники через запятую
     */
    private void saveWhitelist(Long topicId, String csv) {
        whitelistRepository.deleteByTopicId(topicId);
        whitelistRepository.flush();
        if (csv == null || csv.isBlank()) {
            return;
        }
        for (String name : csv.split("[,;]")) {
            String username = name.trim();
            if (username.isEmpty()) {
                continue;
            }
            userRepository.findByUsername(username)
                    .filter(User::isActive)
                    .ifPresent(u -> {
                        TopicWhitelistEntry entry = new TopicWhitelistEntry();
                        entry.setTopicId(topicId);
                        entry.setUserId(u.getId());
                        whitelistRepository.save(entry);
                    });
        }
    }

    /**
     * Возвращает ники белого списка треда через запятую.
     *
     * @param topicId идентификатор треда
     * @return строка ников
     */
    @Transactional(readOnly = true)
    public String getWhitelistCsv(Long topicId) {
        return String.join(", ", getWhitelistNames(topicId));
    }

    /**
     * Возвращает ники белого списка треда.
     *
     * @param topicId идентификатор треда
     * @return отсортированный список ников
     */
    @Transactional(readOnly = true)
    public List<String> getWhitelistNames(Long topicId) {
        List<Long> ids = whitelistRepository.findByTopicId(topicId).stream()
                .map(TopicWhitelistEntry::getUserId).toList();
        return userRepository.findByIdIn(ids).stream()
                .map(User::getUsername).sorted().toList();
    }

    /**
     * Заменяет белый список треда. Доступно владельцу и администратору.
     *
     * @param topicId идентификатор треда
     * @param csv     ники через запятую
     * @param userId  кто редактирует
     * @param isAdmin признак администратора
     */
    @Transactional
    public void updateWhitelist(Long topicId, String csv, Long userId, boolean isAdmin) {
        ForumTopic topic = findTopic(topicId);
        if (!canModerate(topic, userId, isAdmin)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }
        saveWhitelist(topicId, csv);
        log.info("Обновлён белый список треда {}", topicId);
    }

    /**
     * Проверяет, может ли пользователь видеть закрытый тред.
     *
     * @param topic   тред
     * @param userId  пользователь
     * @param isAdmin признак администратора
     * @return true, если доступ разрешён
     */
    private boolean canViewClosed(ForumTopic topic, Long userId, boolean isAdmin) {
        if (topic.getVisibility() == TopicVisibility.OPEN
                && userId != null && isGuest(userId)) {
            return false;
        }
        if (topic.getVisibility() != TopicVisibility.CLOSED) {
            return true;
        }
        if (userId == null) {
            return false;
        }
        if (topic.getAuthorId() != null && topic.getAuthorId().equals(userId)) {
            return true;
        }
        return isAdmin || whitelistRepository.existsByTopicIdAndUserId(topic.getId(), userId);
    }

    /**
     * Может ли пользователь взаимодействовать с закрытым тредом
     * (писать комментарии и ставить реакции). Администратор без
     * членства в белом списке может только читать.
     *
     * @param topic тред
     * @param userId пользователь
     * @return true, если владелец или в белом списке
     */
    private boolean isGuest(Long userId) {
        return userRepository.findById(userId)
                .map(u -> u.getRole() == org.corpsman.auth.model.UserRole.GUEST)
                .orElse(false);
    }

    private boolean canInteractClosed(ForumTopic topic, Long userId) {
        if (topic.getVisibility() != TopicVisibility.CLOSED) {
            return true;
        }
        if (userId == null) {
            return false;
        }
        if (topic.getAuthorId() != null && topic.getAuthorId().equals(userId)) {
            return true;
        }
        return whitelistRepository.existsByTopicIdAndUserId(topic.getId(), userId);
    }

    /**
     * Гард: запрещает действия в закрытом треде неучастникам.
     *
     * @param topicId идентификатор треда
     * @param userId  пользователь
     */
    private void requireClosedInteraction(Long topicId, Long userId) {
        ForumTopic topic = findTopic(topicId);
        if (!canInteractClosed(topic, userId)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED,
                    "Вы не участник белого списка этого закрытого треда");
        }
    }

    /**
     * Добавляет пост-продолжение от владельца треда и сжимает корневые
     * комментарии первого уровня в поддерево предыдущего поста.
     *
     * @param topicId   идентификатор треда
     * @param content   текст продолжения
     * @param anonymous анонимно ли
     * @param userId    автор (только владелец треда)
     * @return созданный пост
     */
    @Transactional
    public ForumPost addContinuation(Long topicId, String content, boolean anonymous, Long userId) {
        ForumTopic topic = findTopic(topicId);
        if (!topic.getAuthorId().equals(userId)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }
        List<ForumPost> existingRoots = postRepository.findByTopicIdOrderByCreatedAtAsc(topicId).stream()
                .filter(p -> p.getParentPostId() == null && !p.isOp())
                .toList();

        ForumPost continuation = new ForumPost();
        continuation.setTopicId(topicId);
        continuation.setContent(content == null ? "" : content.trim());
        continuation.setAuthorId(userId);
        continuation.setAnonymous(anonymous);
        continuation.setContinuation(true);
        ForumPost savedContinuation = postRepository.save(continuation);

        ForumPost previousRoot = existingRoots.stream()
                .filter(ForumPost::isContinuation)
                .reduce((a, b) -> b)
                .orElse(null);
        Long targetParentId = previousRoot != null ? previousRoot.getId()
                : postRepository.findByTopicIdAndOpTrue(topicId).map(ForumPost::getId).orElse(null);
        if (targetParentId != null) {
            List<ForumPost> toReparent = existingRoots.stream()
                    .filter(p -> !p.isContinuation()).toList();
            toReparent.forEach(p -> p.setParentPostId(targetParentId));
            postRepository.saveAll(toReparent);
        }
        log.info("Добавлено продолжение в тред {}, сжато корневых комментариев: {}",
                topicId, existingRoots.size());
        return savedContinuation;
    }

    /**
     * Возвращает видимость треда.
     *
     * @param topicId идентификатор треда
     * @return имя видимости (PUBLIC/OPEN/CLOSED)
     */
    @Transactional(readOnly = true)
    public String getTopicVisibility(Long topicId) {
        ForumTopic topic = findTopic(topicId);
        return topic.getVisibility() == null ? TopicVisibility.OPEN.name() : topic.getVisibility().name();
    }
}
