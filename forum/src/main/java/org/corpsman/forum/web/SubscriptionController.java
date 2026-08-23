package org.corpsman.forum.web;

import lombok.RequiredArgsConstructor;
import org.corpsman.auth.security.SecurityUtils;
import org.corpsman.forum.service.NotificationService;
import org.corpsman.forum.service.SubscriptionService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;


/**
 * Контроллер уведомлений и подписок.
 */
@Controller
@RequiredArgsConstructor
public class SubscriptionController {

    private final NotificationService notificationService;
    private final SubscriptionService subscriptionService;

    /**
     * Страница уведомлений (список + отметка о прочтении).
     */
    @GetMapping("/notifications")
    public String notifications(Model model) {
        Long uid = SecurityUtils.currentUserId();
        model.addAttribute("notifications", notificationService.listFor(uid));
        return "notifications";
    }

    /**
     * Отмечает все уведомления прочитанными.
     */
    @PostMapping("/notifications/read-all")
    public String markAllRead() {
        notificationService.markAllRead(SecurityUtils.currentUserId());
        return "redirect:/notifications";
    }

    /**
     * Переключает подписку на пользователя.
     */
    @PostMapping("/subscriptions/user/{id}/toggle")
    public String toggleUser(@PathVariable Long id) {
        subscriptionService.toggleUserSubscription(SecurityUtils.currentUserId(), id);
        return "redirect:/profile/" + id;
    }

    /**
     * Переключает подписку на тред.
     */
    @PostMapping("/subscriptions/topic/{id}/toggle")
    public String toggleTopic(@PathVariable Long id) {
        subscriptionService.toggleTopicSubscription(SecurityUtils.currentUserId(), id);
        return "redirect:/forum/topic/" + id;
    }
}
