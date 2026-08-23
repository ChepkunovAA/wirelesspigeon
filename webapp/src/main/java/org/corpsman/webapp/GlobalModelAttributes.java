package org.corpsman.webapp;

import lombok.RequiredArgsConstructor;
import org.corpsman.auth.security.SecurityUtils;
import org.corpsman.forum.service.NotificationService;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Глобальные атрибуты моделей (для всех контроллеров).
 */
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAttributes {

    private final NotificationService notificationService;

    /**
     * Количество непрочитанных уведомлений текущего пользователя.
     *
     * @return количество или null для анонима
     */
    @ModelAttribute("unreadNotifications")
    public Long unreadNotifications() {
        var user = SecurityUtils.currentUser();
        return user == null ? null : notificationService.unreadCount(user.getId());
    }
}
