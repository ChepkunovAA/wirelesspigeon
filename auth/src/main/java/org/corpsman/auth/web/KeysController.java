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
     * MASTER — все ключи клуба с возможностью фильтрации по создателю.
     */
@Controller
@RequestMapping("/keys")
@RequiredArgsConstructor
@Slf4j
public class KeysController {

    private final TokenService tokenService;

    /**
     * Отображает страницу ключей с учётом роли и фильтров.
     *
     * @param creator мультивыбор ников администраторов (только для MASTER)
     * @param status  фильтр по статусу: active/used/expired (только для MASTER)
     * @param model   модель представления
     * @return имя представления
     */
    @GetMapping
    public String keys(@RequestParam(required = false) List<String> creator,
                       @RequestParam(required = false) String status,
                       Model model) {
        CorpsmanUserDetails user = SecurityUtils.currentUser();
        boolean master = user != null && user.getRole() == UserRole.MASTER;
        List<InviteTokenDto> tokens;
        if (master) {
            tokens = tokenService.getTokensFiltered(creator, status);
        } else {
            tokens = tokenService.getTokensFor(user == null ? null : user.getRole(), user == null ? null : user.getId());
        }
        model.addAttribute("tokens", tokens);
        model.addAttribute("isMaster", master);
        model.addAttribute("creatorFilter", creator);
        model.addAttribute("status", status);
        if (master) {
            model.addAttribute("creators", tokenService.getAllCreators());
        }
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
        List<InviteTokenDto> tokens;
        if (user != null && user.getRole() == UserRole.MASTER) {
            tokens = tokenService.getTokensFor(UserRole.MASTER, null);
        } else {
            tokens = tokenService.getTokensFor(
                    user == null ? null : user.getRole(), user == null ? null : user.getId());
        }
        model.addAttribute("rawToken", rawToken);
        model.addAttribute("rawIsGuest", effectiveGuest);
        model.addAttribute("tokens", tokens);
        log.info("Создан {} ключ регистрации пользователем {}",
                effectiveGuest ? "гостевой" : "резидентский", SecurityUtils.currentUserId());
        return "keys";
    }
}
