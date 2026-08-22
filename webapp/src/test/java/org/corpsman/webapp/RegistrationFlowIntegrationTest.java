package org.corpsman.webapp;

import jakarta.servlet.http.HttpSession;
import org.corpsman.auth.model.InviteToken;
import org.corpsman.auth.repository.InviteTokenRepository;
import org.corpsman.auth.service.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RegistrationFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private InviteTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void registerWithValidTokenCreatesUserAndAllowsAccess() throws Exception {
        String raw = tokenService.generateToken(null);

        MvcResult result = mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("token", raw)
                        .param("username", "alice")
                        .param("password", "password123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();

        HttpSession session = result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY))
                .as("после регистрации должна быть создана сессия с аутентификацией")
                .isNotNull();

        mockMvc.perform(get("/").session((org.springframework.mock.web.MockHttpSession) session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Выйти")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/cinema")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/profile\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Войти"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("anonymousUser"))));

        mockMvc.perform(get("/profile").session((org.springframework.mock.web.MockHttpSession) session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("alice")));
    }

    @Test
    void registerWithExpiredTokenShowsErrorAndNoSession() throws Exception {
        InviteToken token = new InviteToken();
        token.setCodeHash(passwordEncoder.encode("expired-raw"));
        token.setExpiresAt(LocalDateTime.now().minusDays(1));
        token.setUsed(false);
        tokenRepository.save(token);

        MvcResult result = mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("token", "expired-raw")
                        .param("username", "bob")
                        .param("password", "password123"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Токен истек")))
                .andReturn();

        HttpSession session = result.getRequest().getSession(false);
        assertThat(session)
                .as("при ошибке регистрации не должно быть сессии с аутентификацией")
                .satisfiesAnyOf(
                        s -> assertThat(s).isNull(),
                        s -> assertThat(s.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNull());
    }

    @Test
    void homePageAccessibleToUnauthenticatedUsers() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Зарегистрироваться")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Войти")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Следующий фильм")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Последний просмотренный")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("anonymousUser"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Выйти"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("/cinema"))));
    }

    @Test
    void aboutPageNoLongerExists() throws Exception {
        mockMvc.perform(get("/about"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void logoutRedirectsToHomePage() throws Exception {
        String raw = tokenService.generateToken(null);
        MvcResult result = mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("token", raw)
                        .param("username", "logout_user")
                        .param("password", "password123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();

        mockMvc.perform(post("/logout").session((org.springframework.mock.web.MockHttpSession) result.getRequest().getSession(false))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void authenticatedUserVisitingLoginIsRedirectedHome() throws Exception {
        String raw = tokenService.generateToken(null);
        MvcResult result = mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("token", raw)
                        .param("username", "login_redirect_user")
                        .param("password", "password123"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        mockMvc.perform(get("/login").session((org.springframework.mock.web.MockHttpSession) result.getRequest().getSession(false)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }
}