package org.corpsman.auth.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.corpsman.auth.model.User;
import org.corpsman.auth.model.UserRole;
import org.corpsman.auth.repository.UserRepository;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Контроллер страницы настроек пользователя.
 */
@Controller
@RequestMapping("/settings")
@RequiredArgsConstructor
@Slf4j
public class SettingsController {

    private final SettingsService settingsService;
    private final UserRepository userRepository;

    /**
     * Отображает страницу настроек.
     *
     * @param model модель представления
     * @return имя представления
     */
    @GetMapping
    public String settings(Model model) {
        model.addAttribute("settings", settingsService.getByUserId(SecurityUtils.currentUserId()));
        User user = userRepository.findById(SecurityUtils.currentUserId()).orElse(null);
        model.addAttribute("avatarPath", user != null ? user.getAvatarPath() : null);
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

    /**
     * Загружает аватарку пользователя.
     *
     * @param file загружаемый файл
     * @param ra    flash-атрибуты для сообщений
     * @return редирект на страницу настроек
     */
    @PostMapping("/avatar")
    public String uploadAvatar(@RequestParam("file") MultipartFile file,
                               RedirectAttributes ra) {
        try {
            String path = settingsService.uploadAvatar(SecurityUtils.currentUserId(), file);
            ra.addFlashAttribute("success", "Аватарка обновлена");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/settings";
    }

    /**
     * Удаляет аватарку пользователя.
     *
     * @param ra flash-атрибуты для сообщений
     * @return редирект на страницу настроек
     */
    @PostMapping("/avatar/delete")
    public String deleteAvatar(RedirectAttributes ra) {
        try {
            settingsService.deleteAvatar(SecurityUtils.currentUserId());
            ra.addFlashAttribute("success", "Аватарка удалена");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/settings";
    }
}
