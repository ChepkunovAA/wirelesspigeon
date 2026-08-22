package org.corpsman.users.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.corpsman.auth.security.SecurityUtils;
import org.corpsman.auth.service.SettingsService;
import org.corpsman.common.exception.BusinessException;
import org.corpsman.users.service.MemberService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Контроллер страницы «Участники».
 */
@Controller
@RequestMapping("/users")
@RequiredArgsConstructor
@Slf4j
public class MembersController {

    private final MemberService memberService;

    private final SettingsService settingsService;

    /**
     * Отображает список участников клуба.
     *
     * @param search поиск по нику
     * @param page   номер страницы (с нуля)
     * @param model  модель представления
     * @return имя представления
     */
    @GetMapping
    public String members(@RequestParam(defaultValue = "") String search,
                          @RequestParam(defaultValue = "0") int page,
                          Model model) {
        Pageable pageable = PageRequest.of(page, 20, Sort.by("username").ascending());
        model.addAttribute("members", memberService.getMembers(search, pageable));
        model.addAttribute("search", search);
        model.addAttribute("currentUserId", SecurityUtils.currentUserId());
        return "users/members";
    }

    /**
     * Банит пользователя (удаляет с сайта). Доступно только администратору.
     *
     * @param id                идентификатор пользователя
     * @param redirectAttributes flash-атрибуты для сообщений
     * @return редирект на список участников
     */
    @PostMapping("/{id}/ban")
    public String ban(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            memberService.banUser(id, SecurityUtils.currentUserId());
            redirectAttributes.addFlashAttribute("success", "Пользователь забанен и удалён с сайта");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/users";
    }

    /**
     * Сбрасывает пароль пользователя. Доступно только администратору.
     *
     * @param id          идентификатор пользователя
     * @param newPassword новый пароль
     * @param ra          flash-атрибуты для сообщений
     * @return редирект на список участников
     */
    /**
     * Повышает гостя до резидента. Доступно только мастеру.
     */
    @PostMapping("/{id}/promote")
    public String promote(@PathVariable Long id, RedirectAttributes ra) {
        try {
            memberService.promoteToResident(id);
            ra.addFlashAttribute("success", "Пользователь повышен до резидента");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/users";
    }

    @PostMapping("/{id}/reset-password")
    public String resetPassword(@PathVariable Long id, RedirectAttributes ra) {
        try {
            String raw = settingsService.generateAndReset(id);
            ra.addFlashAttribute("success",
                    "Пароль сброшен. Новый пароль: " + raw + " — передайте его пользователю.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/users";
    }
}
