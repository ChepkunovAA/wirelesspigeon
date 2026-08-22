package org.corpsman.auth.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
     * @param showPublicPosts   показывать публичные посты
     * @param ra               flash-атрибуты для сообщений
     * @return редирект на страницу настроек
     */
    @PostMapping("/prefs")
    public String updatePrefs(@RequestParam(defaultValue = "false") boolean defaultThreadAnonymous,
                              @RequestParam(defaultValue = "false") boolean defaultCommentAnonymous,
                              @RequestParam(defaultValue = "OPEN") String defaultVisibility,
                              @RequestParam(defaultValue = "false") boolean showPublicPosts,
                              RedirectAttributes ra) {
        settingsService.update(SecurityUtils.currentUserId(), defaultThreadAnonymous,
                defaultCommentAnonymous, defaultVisibility, showPublicPosts);
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
