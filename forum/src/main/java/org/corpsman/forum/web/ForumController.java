package org.corpsman.forum.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.corpsman.auth.model.UserRole;
import org.corpsman.common.exception.BusinessException;
import org.corpsman.common.exception.ErrorCode;
import org.corpsman.forum.model.ForumAttachment;
import org.corpsman.auth.security.CorpsmanUserDetails;
import org.corpsman.auth.security.SecurityUtils;
import org.corpsman.common.exception.BusinessException;
import org.corpsman.forum.dto.ForumPostRequest;
import org.corpsman.forum.dto.LikeResponse;
import org.corpsman.forum.dto.TopicCreateRequest;
import org.corpsman.forum.dto.TopicDetailDto;
import org.corpsman.forum.service.AttachmentService;
import org.corpsman.forum.service.FileStorageService;
import org.corpsman.auth.repository.UserRepository;
import org.corpsman.auth.service.SettingsService;
import org.corpsman.auth.model.UserRole;
import org.corpsman.forum.service.ForumService;
import org.corpsman.forum.service.PostService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Контроллер форума: треды, комментарии, лайки.
 */
@Controller
@RequestMapping("/forum")
@RequiredArgsConstructor
@Slf4j
public class ForumController {

    private static final int TOPICS_PAGE_SIZE = 20;
    private static final int POSTS_PAGE_SIZE = 20;

    private final ForumService forumService;

    private final SettingsService settingsService;

    private final UserRepository userRepository;
    private final PostService postService;
    private final AttachmentService attachmentService;
    private final FileStorageService fileStorageService;

    /**
     * Отображает список тредов с поиском и фильтрацией по тегам.
     *
     * @param page    номер страницы
     * @param search  поиск по названию
     * @param tag     фильтр по тегу
     * @param model   модель представления
     * @return имя представления
     */
    @GetMapping
    public String topicList(@RequestParam(defaultValue = "0") int page,
                            @RequestParam(required = false) String search,
                            @RequestParam(required = false) String tag,
                            Model model) {
        Pageable pageable = PageRequest.of(page, TOPICS_PAGE_SIZE);
        List<String> tags = tag == null || tag.isBlank() ? null : List.of(tag);
        boolean hidePublic = SecurityUtils.currentUserId() != null
                && !settingsService.getByUserId(SecurityUtils.currentUserId()).isShowPublicPosts();
        CorpsmanUserDetails viewer = SecurityUtils.currentUser();
        boolean guestRole = viewer != null && viewer.getRole() == UserRole.GUEST;
        model.addAttribute("page", forumService.getTopics(pageable, search, tags, hidePublic,
                guestRole, SecurityUtils.currentUserId()));
        model.addAttribute("search", search);
        model.addAttribute("selectedTag", tag);
        model.addAttribute("tags", forumService.getPopularTags());
        return "forum/list";
    }

    /**
     * Отображает форму создания треда.
     *
     * @param model модель представления
     * @return имя представления
     */
    @GetMapping("/topic/create")
    public String showCreateForm(Model model) {
        TopicCreateRequest topicForm = new TopicCreateRequest();
        CorpsmanUserDetails viewer = SecurityUtils.currentUser();
        var userSettings = settingsService.getByUserId(SecurityUtils.currentUserId());
        topicForm.setAnonymous(userSettings.isDefaultThreadAnonymous());
        topicForm.setReadOnly(userSettings.isDefaultReadOnly());
        String defaultVis = userSettings.getDefaultVisibility();
        if (viewer != null && viewer.getRole() == UserRole.GUEST) {
            defaultVis = "GUEST";
        }
        topicForm.setVisibility(defaultVis);
        boolean guestCreator = viewer != null && viewer.getRole() == UserRole.GUEST;
        model.addAttribute("guestCreator", guestCreator);
        model.addAttribute("topic", topicForm);
        model.addAttribute("allTags", forumService.getAllTags());
        Long me = SecurityUtils.currentUserId();
        model.addAttribute("allUsers", userRepository.findAll().stream()
                .filter(u -> u.isActive() && !u.getId().equals(me))
                .toList());
        return "forum/create";
    }

    /**
     * Создаёт новый тред.
     *
     * @param dto    данные треда
     * @param result результат валидации
     * @param model  модель представления
     * @return редирект на тред или форма с ошибками
     */
    @PostMapping("/topic/create")
    public String createTopic(@Valid @ModelAttribute("topic") TopicCreateRequest dto,
                              BindingResult result,
                              @RequestParam(value = "files", required = false) List<MultipartFile> files,
                              @RequestParam(value = "whitelistItems", required = false) List<String> whitelistItems,
                              Model model) {
        if (whitelistItems != null) {
            dto.setWhitelist(String.join(",", whitelistItems));
        }
        if (result.hasErrors()) {
            model.addAttribute("allTags", forumService.getAllTags());
            return "forum/create";
        }
        var topic = forumService.createTopic(dto, SecurityUtils.currentUserId(), files);
        return "redirect:/forum/topic/" + topic.getId();
    }

    /**
     * Отображает тред с деревом комментариев.
     *
     * @param id      идентификатор треда
     * @param page    номер страницы корневых комментариев
     * @param model   модель представления
     * @return имя представления
     */
    @GetMapping("/topic/{id}")
    public String topicDetail(@PathVariable Long id,
                              @RequestParam(defaultValue = "0") int page,
                              Model model) {
        Pageable pageable = PageRequest.of(page, POSTS_PAGE_SIZE);
        TopicDetailDto topic = forumService.getTopic(id, SecurityUtils.currentUserId(), pageable);
        model.addAttribute("topic", topic);
        ForumPostRequest form = new ForumPostRequest();
        form.setTopicId(id);
        form.setAnonymous(settingsService.getByUserId(SecurityUtils.currentUserId()).isDefaultCommentAnonymous());
        model.addAttribute("commentForm", form);
        model.addAttribute("currentUserId", SecurityUtils.currentUserId());
        boolean isOwner = SecurityUtils.currentUserId() != null
                && SecurityUtils.currentUserId().equals(topic.getTopicAuthorId());
        model.addAttribute("isTopicOwner", isOwner);
        model.addAttribute("visibility", forumService.getTopicVisibility(id));
        model.addAttribute("whitelistNames", forumService.getWhitelistNames(id));
        Long viewerId = SecurityUtils.currentUserId();
        model.addAttribute("allUsers", userRepository.findAll().stream()
                .filter(u -> u.isActive()
                        && !u.getId().equals(viewerId)
                        && (topic.getTopicAuthorId() == null || !u.getId().equals(topic.getTopicAuthorId())))
                .toList());
        return "forum/topic";
    }

    /**
     * Отображает форму редактирования первого поста треда.
     *
     * @param id    идентификатор треда
     * @param model модель представления
     * @return имя представления
     */
    @GetMapping("/topic/{id}/edit")
    public String showEditTopicForm(@PathVariable Long id, Model model) {
        model.addAttribute("topicId", id);
        model.addAttribute("content", forumService.getTopic(id, SecurityUtils.currentUserId(), PageRequest.of(0, 1)).getFirstPost().getContent());
        return "forum/edit-topic";
    }

    /**
     * Сохраняет изменения первого поста треда.
     *
     * @param id      идентификатор треда
     * @param content новое содержание
     * @return редирект на тред
     */
    @PostMapping("/topic/{id}/edit")
    public String editTopic(@PathVariable Long id, @RequestParam String content) {
        forumService.editTopic(id, content, SecurityUtils.currentUserId());
        return "redirect:/forum/topic/" + id;
    }

    /**
     * Добавляет комментарий в тред (в том числе ответ на комментарий).
     *
     * @param id        идентификатор треда
     * @param content   текст комментария
     * @param parentId  идентификатор родительского комментария (null для корневого)
     * @param anonymous признак анонимности
     * @return редирект на тред
     */
    @PostMapping("/topic/{id}/comment")
    public String addComment(@PathVariable Long id,
                             @RequestParam String content,
                             @RequestParam(required = false) Long parentId,
                             @RequestParam(required = false) Long replyToId,
                             @RequestParam(defaultValue = "false") boolean anonymous,
                             @RequestParam(value = "files", required = false) List<MultipartFile> files) {
        ForumPostRequest form = new ForumPostRequest();
        form.setTopicId(id);
        form.setParentPostId(parentId);
        form.setReplyToId(replyToId);
        form.setContent(content);
        form.setAnonymous(anonymous);
        var created = postService.createPost(form, SecurityUtils.currentUserId(), files);
        String anchor = "redirect:/forum/topic/" + id;
        if (created.getParentPostId() != null) {
            anchor += "?open=" + created.getParentPostId();
        }
        return anchor + "#post-" + created.getId();
    }

    /**
     * Отображает форму редактирования комментария.
     *
     * @param id    идентификатор поста
     * @param model модель представления
     * @return имя представления
     */
    @GetMapping("/post/{id}/edit")
    public String showEditPostForm(@PathVariable Long id, Model model) {
        var post = postService.getPost(id);
        model.addAttribute("postId", id);
        model.addAttribute("topicId", post.getTopicId());
        model.addAttribute("content", post.getContent());
        return "forum/edit-post";
    }

    /**
     * Сохраняет изменения комментария.
     *
     * @param id      идентификатор поста
     * @param content новое содержание
     * @return редирект на тред
     */
    @PostMapping("/post/{id}/edit")
    public String editPost(@PathVariable Long id, @RequestParam String content) {
        var post = postService.getPost(id);
        postService.editPost(id, content, SecurityUtils.currentUserId());
        return "redirect:/forum/topic/" + post.getTopicId();
    }

    /**
     * Удаляет комментарий.
     *
     * @param id идентификатор поста
     * @return редирект на тред
     */
    @PostMapping("/post/{id}/delete")
    public String deletePost(@PathVariable Long id) {
        var post = postService.getPost(id);
        postService.deletePost(id, SecurityUtils.currentUserId(), isAdmin());
        return "redirect:/forum/topic/" + post.getTopicId();
    }

    /**
     * Отдаёт вложение: фото/видео просматриваются inline, прочие файлы скачиваются.
     *
     * @param id идентификатор вложения
     * @return файл
     */
    @GetMapping("/attachment/{id}")
    public ResponseEntity<Resource> getAttachment(@PathVariable Long id) {
        return serveAttachment(attachmentService.getAttachment(id));
    }

    /**
     * Публично отдаёт вложение из публичного треда для главной страницы.
     *
     * @param id идентификатор вложения
     * @return файл
     */
    @GetMapping("/public/attachment/{id}")
    public ResponseEntity<Resource> getPublicAttachment(@PathVariable Long id) {
        var attachment = attachmentService.getAttachment(id);
        if (attachment.getTopicId() == null || !forumService.isPublicTopic(attachment.getTopicId())) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        return serveAttachment(attachment);
    }

    private ResponseEntity<Resource> serveAttachment(ForumAttachment attachment) {
        Resource resource = new FileSystemResource(fileStorageService.load(attachment.getStoredName()));
        boolean inline = attachment.getContentType().startsWith("image/")
                || attachment.getContentType().startsWith("video/");
        ContentDisposition disposition = inline
                ? ContentDisposition.inline().filename(attachment.getFileName(), java.nio.charset.StandardCharsets.UTF_8).build()
                : ContentDisposition.attachment().filename(attachment.getFileName(), java.nio.charset.StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getContentType()))
                .contentLength(attachment.getSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(resource);
    }

    /**
     * Лайк комментария (AJAX).
     *
     * @param id идентификатор поста
     * @return результат лайка
     */
    @PostMapping("/post/{id}/like")
    @ResponseBody
    public ResponseEntity<?> likePost(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(postService.likePost(id, SecurityUtils.currentUserId()));
        } catch (BusinessException ex) {
            return ResponseEntity.status(ex.getErrorCode().getHttpStatus())
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    /**
     * Снимает лайк (AJAX).
     *
     * @param id идентификатор поста
     * @return результат снятия лайка
     */
    @PostMapping("/post/{id}/unlike")
    @ResponseBody
    public ResponseEntity<LikeResponse> unlikePost(@PathVariable Long id) {
        return ResponseEntity.ok(postService.unlikePost(id, SecurityUtils.currentUserId()));
    }

    /**
     * Дизлайк комментария (AJAX).
     *
     * @param id идентификатор поста
     * @return результат дизлайка
     */
    @PostMapping("/post/{id}/dislike")
    @ResponseBody
    public ResponseEntity<?> dislikePost(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(postService.dislikePost(id, SecurityUtils.currentUserId()));
        } catch (BusinessException ex) {
            return ResponseEntity.status(ex.getErrorCode().getHttpStatus())
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    /**
     * Снимает дизлайк (AJAX).
     *
     * @param id идентификатор поста
     * @return результат снятия дизлайка
     */
    @PostMapping("/post/{id}/undislike")
    @ResponseBody
    public ResponseEntity<LikeResponse> undislikePost(@PathVariable Long id) {
        return ResponseEntity.ok(postService.undislikePost(id, SecurityUtils.currentUserId()));
    }

    /**
     * Лайк первого поста треда (AJAX).
     *
     * @param id идентификатор треда
     * @return результат лайка
     */
    @PostMapping("/topic/{id}/like")
    @ResponseBody
    public ResponseEntity<?> likeTopic(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(forumService.likeTopic(id, SecurityUtils.currentUserId()));
        } catch (BusinessException ex) {
            return ResponseEntity.status(ex.getErrorCode().getHttpStatus())
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    /**
     * Снимает лайк с первого поста треда (AJAX).
     *
     * @param id идентификатор треда
     * @return результат снятия лайка
     */
    @PostMapping("/topic/{id}/unlike")
    @ResponseBody
    public ResponseEntity<LikeResponse> unlikeTopic(@PathVariable Long id) {
        return ResponseEntity.ok(forumService.unlikeTopic(id, SecurityUtils.currentUserId()));
    }

    /**
     * Дизлайк первого поста треда (AJAX).
     *
     * @param id идентификатор треда
     * @return результат дизлайка
     */
    @PostMapping("/topic/{id}/dislike")
    @ResponseBody
    public ResponseEntity<?> dislikeTopic(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(forumService.dislikeTopic(id, SecurityUtils.currentUserId()));
        } catch (BusinessException ex) {
            return ResponseEntity.status(ex.getErrorCode().getHttpStatus())
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    /**
     * Снимает дизлайк с первого поста треда (AJAX).
     *
     * @param id идентификатор треда
     * @return результат снятия дизлайка
     */
    @PostMapping("/topic/{id}/undislike")
    @ResponseBody
    public ResponseEntity<LikeResponse> undislikeTopic(@PathVariable Long id) {
        return ResponseEntity.ok(forumService.undislikeTopic(id, SecurityUtils.currentUserId()));
    }

    private boolean isAdmin() {
        CorpsmanUserDetails user = SecurityUtils.currentUser();
        return user != null && user.getRole() == UserRole.MASTER;
    }

    /**
     * Добавляет пост-продолжение (только владелец треда) со сжатием корневых комментариев.
     *
     * @param id      идентификатор треда
     * @param content текст продолжения
     * @param files   вложения
     * @return редирект на тред
     */
    @PostMapping("/topic/{id}/continuation")
    public String addContinuation(@PathVariable Long id,
                                  @RequestParam String content,
                                  @RequestParam(defaultValue = "false") boolean anonymous,
                                  @RequestParam(value = "files", required = false) List<MultipartFile> files) {
        forumService.addContinuation(id, content, anonymous,
                SecurityUtils.currentUserId());
        return "redirect:/forum/topic/" + id;
    }

    /**
     * Сохраняет белый список закрытого треда.
     *
     * @param id      идентификатор треда
     * @param whitelist ники через запятую
     * @return редирект на тред
     */
    @PostMapping("/topic/{id}/whitelist")
    public String updateWhitelist(@PathVariable Long id,
                                  @RequestParam(required = false) List<String> whitelistItems) {
        forumService.updateWhitelist(id, whitelistItems == null ? ""
                : String.join(",", whitelistItems), SecurityUtils.currentUserId(), isAdmin());
        return "redirect:/forum/topic/" + id;
    }
}

