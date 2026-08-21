package org.corpsman.forum.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.corpsman.auth.model.UserRole;
import org.corpsman.auth.security.CorpsmanUserDetails;
import org.corpsman.auth.security.SecurityUtils;
import org.corpsman.forum.service.ForumService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Контроллер модерации форума: заморозка, удаление, баны.
 */
@Controller
@RequestMapping("/forum/topic/{topicId}/moderation")
@RequiredArgsConstructor
@Slf4j
public class ForumModerationController {

    private final ForumService forumService;

    /**
     * Замораживает тред.
     *
     * @param topicId идентификатор треда
     * @return редирект на тред
     */
    @PostMapping("/freeze")
    public String freeze(@PathVariable Long topicId) {
        forumService.freezeTopic(topicId, SecurityUtils.currentUserId(), isAdmin());
        return "redirect:/forum/topic/" + topicId;
    }

    /**
     * Размораживает тред.
     *
     * @param topicId идентификатор треда
     * @return редирект на тред
     */
    @PostMapping("/unfreeze")
    public String unfreeze(@PathVariable Long topicId) {
        forumService.unfreezeTopic(topicId, SecurityUtils.currentUserId(), isAdmin());
        return "redirect:/forum/topic/" + topicId;
    }

    /**
     * Удаляет тред.
     *
     * @param topicId идентификатор треда
     * @return редирект на список тредов
     */
    @PostMapping("/delete")
    public String delete(@PathVariable Long topicId) {
        forumService.deleteTopic(topicId, SecurityUtils.currentUserId(), isAdmin());
        return "redirect:/forum";
    }

    /**
     * Банит пользователя в треде.
     *
     * @param topicId идентификатор треда
     * @param userId  идентификатор пользователя
     * @return редирект на тред
     */
    @PostMapping("/ban")
    public String ban(@PathVariable Long topicId,
                      @RequestParam Long userId,
                      @RequestParam(required = false) String displayName) {
        forumService.banUser(topicId, userId, SecurityUtils.currentUserId(), isAdmin(), displayName);
        return "redirect:/forum/topic/" + topicId;
    }

    /**
     * Разбанивает пользователя в треде.
     *
     * @param topicId идентификатор треда
     * @param userId  идентификатор пользователя
     * @return редирект на тред
     */
    @PostMapping("/unban")
    public String unban(@PathVariable Long topicId, @RequestParam Long userId) {
        forumService.unbanUser(topicId, userId, SecurityUtils.currentUserId(), isAdmin());
        return "redirect:/forum/topic/" + topicId;
    }

    private boolean isAdmin() {
        CorpsmanUserDetails user = SecurityUtils.currentUser();
        return user != null && user.getRole() == UserRole.ADMIN;
    }
}