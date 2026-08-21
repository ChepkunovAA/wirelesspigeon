package org.corpsman.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.corpsman.auth.security.SecurityUtils;
import org.corpsman.auth.service.AuthService;
import org.corpsman.common.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Контроллер входа и регистрации.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    /**
     * Отображает страницу входа.
     * <p>
     * Уже аутентифицированные пользователи перенаправляются на главную.
     *
     * @param error признак ошибки входа
     * @param model  модель представления
     * @return имя представления или редирект на главную
     */
    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String error, Model model) {
        if (SecurityUtils.currentUser() != null) {
            return "redirect:/";
        }
        if (error != null) {
            model.addAttribute("error", "Неверный логин или пароль");
        }
        return "auth/login";
    }

    /**
     * Выполняет аутентификацию пользователя.
     *
     * @param username имя пользователя
     * @param password пароль
     * @param request  текущий запрос
     * @return редирект на главную страницу или страницу входа с ошибкой
     */
    @PostMapping("/login")
    public String login(@RequestParam String username, @RequestParam String password, HttpServletRequest request) {
        try {
            Authentication authentication = authService.login(username, password);
            establishSession(request, authentication);
            log.info("Пользователь {} вошёл в систему", username);
            return "redirect:/";
        } catch (AuthenticationException ex) {
            log.warn("Неудачная попытка входа для пользователя: {}", username);
            return "redirect:/login?error";
        }
    }

    /**
     * Отображает страницу регистрации.
     *
     * @param token инвайт-ключ
     * @param model модель представления
     * @return имя представления
     */
    @GetMapping("/register")
    public String registerPage(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("token", token == null ? "" : token);
        return "auth/register";
    }

    /**
     * Регистрирует нового пользователя по ключу и автоматически выполняет вход.
     *
     * @param token    инвайт-ключ
     * @param username имя пользователя
     * @param password пароль
     * @param request  текущий запрос
     * @param model    модель представления
     * @return редирект на главную страницу или страницу регистрации с ошибкой
     */
    @PostMapping("/register")
    public String register(@RequestParam String token,
                           @RequestParam String username,
                           @RequestParam String password,
                           HttpServletRequest request,
                           Model model) {
        try {
            authService.register(token, username, password);
            Authentication authentication = authService.login(username, password);
            establishSession(request, authentication);
            log.info("Новый пользователь зарегистрирован: {}", username);
            return "redirect:/";
        } catch (BusinessException ex) {
            log.warn("Регистрация не выполнена для {}: {}", username, ex.getMessage());
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("token", token);
            return "auth/register";
        }
    }

    private void establishSession(HttpServletRequest request, Authentication authentication) {
        request.getSession(true);
        request.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        request.getSession(true).setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    }
}