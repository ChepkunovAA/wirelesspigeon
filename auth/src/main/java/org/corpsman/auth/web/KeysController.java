package org.corpsman.auth.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.corpsman.auth.dto.InviteTokenDto;
import org.corpsman.auth.model.UserRole;
import org.corpsman.auth.security.CorpsmanUserDetails;
import org.corpsman.auth.security.SecurityUtils;
import org.corpsman.auth.service.TokenService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * Контроллер страницы «Ключи» для RESIDENT и MASTER.
 * <p>
 * RESIDENT видит только созданные им гостевые ключи,
 * MASTER — все ключи клуба.
 */
@Controller
@RequestMapping("/keys")
@RequiredArgsConstructor
@Slf4j
public class KeysController {

    private final TokenService tokenService;

    /**
     * Отображает страницу ключей с учётом роли.
     *
     * @param model модель представления
     * @return имя представления
     */
    @GetMapping
    public String keys(Model model) {
        CorpsmanUserDetails user = SecurityUtils.currentUser();
        boolean master = user != null && user.getRole() == UserRole.MASTER;
        List<InviteTokenDto> tokens =
                tokenService.getTokensFor(user == null ? null : user.getRole(), user == null ? null : user.getId());
        model.addAttribute("tokens", tokens);
        model.addAttribute("isMaster", master);
        return "keys";
    }

    /**
     * Генерирует новый ключ. RESIDENT создаёт только гостевые ключи,
     * MASTER может выбрать тип (гостевой/резидентский).
     *
     * @param guestKey признак гостевого ключа (для MASTER)
     * @param model    модель представления
     * @return имя представления
     */
    @PostMapping("/generate")
    public String generate(@RequestParam(defaultValue = "false") boolean guestKey, Model model) {
        CorpsmanUserDetails user = SecurityUtils.currentUser();
        boolean resident = user != null && user.getRole() == UserRole.RESIDENT;
        boolean effectiveGuest = resident || guestKey;
        String rawToken = tokenService.generateToken(SecurityUtils.currentUserId(), effectiveGuest);
        List<InviteTokenDto> tokens = tokenService.getTokensFor(
                user == null ? null : user.getRole(), user == null ? null : user.getId());
        model.addAttribute("rawToken", rawToken);
        model.addAttribute("rawIsGuest", effectiveGuest);
        model.addAttribute("tokens", tokens);
        log.info("Создан {} ключ регистрации пользователем {}",
                effectiveGuest ? "гостевой" : "резидентский", SecurityUtils.currentUserId());
        return "keys";
    }
}
