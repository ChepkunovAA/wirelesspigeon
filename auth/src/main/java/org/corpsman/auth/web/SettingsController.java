package org.corpsman.auth.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.corpsman.auth.model.UserRole;
import org.corpsman.auth.security.CorpsmanUserDetails;
import org.corpsman.auth.security.SecurityUtils;
import org.corpsman.auth.service.SettingsService;
import org.corpsman.common.exception.BusinessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Контроллер страницы настроек пользователя.
 */
@Controller
@RequestMapping("/settings")
@RequiredArgsConstructor
@Slf4j
public class SettingsController {

    private final SettingsService settingsService;

    /**
     * Отображает страницу настроек.
     *
     * @param model модель представления
     * @return имя представления
     */
    @GetMapping
    public String settings(Model model) {
        model.addAttribute("settings", settingsService.getByUserId(SecurityUtils.currentUserId()));
        return "settings";
    }

    /**
     * Сохраняет настройки по умолчанию.
     *
     * @param threadAnonymous   треды анонимные по умолчанию
     * @param commentAnonymous  комментарии анонимные по умолчанию
     * @param defaultVisibility видимость новых тредов по умолчанию
     * @param defaultReadOnly   новые треды только для чтения
     * @param showPublicPosts   показывать публичные посты
     * @param hideGuestThreads  скрывать гостевые треды
     * @param ra               flash-атрибуты для сообщений
     * @return редирект на страницу настроек
     */
    @PostMapping("/prefs")
    public String updatePrefs(@RequestParam(defaultValue = "false") boolean defaultThreadAnonymous,
                              @RequestParam(defaultValue = "false") boolean defaultCommentAnonymous,
                              @RequestParam(defaultValue = "OPEN") String defaultVisibility,
                              @RequestParam(defaultValue = "false") boolean defaultReadOnly,
                              @RequestParam(defaultValue = "false") boolean showPublicPosts,
                              @RequestParam(defaultValue = "false") boolean hideGuestThreads,
                              RedirectAttributes ra) {
        CorpsmanUserDetails actor = SecurityUtils.currentUser();
        var existingSettings = settingsService.getByUserId(SecurityUtils.currentUserId());
        String effectiveVis = (actor != null && actor.getRole() == UserRole.GUEST)
                ? existingSettings.getDefaultVisibility()
                : defaultVisibility;
        settingsService.update(SecurityUtils.currentUserId(), defaultThreadAnonymous,
                defaultCommentAnonymous, effectiveVis, defaultReadOnly, showPublicPosts,
                hideGuestThreads);
        ra.addFlashAttribute("success", "Настройки сохранены");
        return "redirect:/settings";
    }

    /**
     * Меняет пароль пользователя.
     *
     * @param oldPassword текущий пароль
     * @param newPassword новый пароль
     * @param ra          flash-атрибуты для сообщений
     * @return редирект на страницу настроек
     */
    @PostMapping("/password")
    public String changePassword(@RequestParam String oldPassword,
                                 @RequestParam String newPassword,
                                 RedirectAttributes ra) {
        try {
            settingsService.changePassword(SecurityUtils.currentUserId(), oldPassword, newPassword);
            ra.addFlashAttribute("success", "Пароль изменён");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/settings";
    }
}
