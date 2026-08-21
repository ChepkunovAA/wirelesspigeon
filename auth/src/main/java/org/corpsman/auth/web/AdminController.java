package org.corpsman.auth.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.corpsman.auth.model.InviteToken;
import org.corpsman.auth.security.SecurityUtils;
import org.corpsman.auth.service.TokenService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * Административный контроллер управления ключами регистрации.
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final TokenService tokenService;

    /**
     * Отображает список всех ключей регистрации.
     *
     * @param model модель представления
     * @return имя представления
     */
    @GetMapping("/tokens")
    public String tokens(Model model) {
        model.addAttribute("tokens", tokenService.getAllTokens());
        return "admin/tokens";
    }

    /**
     * Генерирует новый ключ регистрации и показывает его администратору.
     *
     * @param model модель представления
     * @return имя представления
     */
    @PostMapping("/generate")
    public String generate(Model model) {
        String rawToken = tokenService.generateToken(SecurityUtils.currentUserId());
        List<InviteToken> tokens = tokenService.getAllTokens();
        model.addAttribute("rawToken", rawToken);
        model.addAttribute("tokens", tokens);
        log.info("Администратором создан новый ключ регистрации, всего ключей: {}", tokens.size());
        return "admin/tokens";
    }
}