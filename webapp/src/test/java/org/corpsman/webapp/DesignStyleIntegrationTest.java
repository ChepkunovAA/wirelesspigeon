package org.corpsman.webapp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DesignStyleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void maskTileAssetIsPubliclyServed() throws Exception {
        mockMvc.perform(get("/assets/mask-tile.png"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));
    }

    @Test
    void appCssReferencesMaskTileBackground() throws Exception {
        mockMvc.perform(get("/css/app.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("mask-tile.png")));
    }

    @Test
    void navbarShowsMaskLogoOnLoginPage() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/assets/logo.png")));
    }

    @Test
    void homeShowsPublicPostPlaceholderWhenNoneExists() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Постов пока нет")));
    }

    @Test
    void anonymousHomeHasNoDuplicateLoginButtons() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Зарегистрироваться")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        containsString("Регистрация по ключу"))));
    }

    @Test
    void darkThemeAssetsArePubliclyServed() throws Exception {
        mockMvc.perform(get("/assets/dark-asset.png"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));
        mockMvc.perform(get("/assets/mask-tile-dark.png"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));
    }

    @Test
    void themeToggleButtonPresentOnNavPages() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"theme-toggle\"")))
                .andExpect(content().string(containsString("/js/theme.js")));
    }

    @Test
    void themeToggleButtonPresentOnStandalonePages() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"theme-toggle\"")))
                .andExpect(content().string(containsString("/js/theme.js")));
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"theme-toggle\"")));
    }

    @Test
    void themeJsScriptIsPubliclyServed() throws Exception {
        mockMvc.perform(get("/js/theme.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("corpsman-theme")));
    }

    @Test
    void themeJsToggleHasRobustFallbackToStoredTheme() throws Exception {
        // если атрибут data-bs-theme на момент клика отсутствует — тема берётся из хранилища,
        // иначе каждый клик сводился бы к «всегда в тёмную» (переключатель работал бы в одну сторону)
        mockMvc.perform(get("/js/theme.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("getAttribute('data-bs-theme') || currentTheme()")))
                // идемпотентность: повторная загрузка скрипта не создаёт дубль-слушателей
                .andExpect(content().string(containsString("__corpsmanThemeInit")))
                // слушатель вешается и при уже отработавшем DOMContentLoaded
                .andExpect(content().string(containsString("document.readyState === 'loading'")));
    }

    @Test
    void navbarShowsDarkLogoImageElement() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("logo-dark")))
                .andExpect(content().string(containsString("/assets/dark-asset.png")));
    }

    @Test
    void appCssHasDarkThemeBlock() throws Exception {
        mockMvc.perform(get("/css/app.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-bs-theme=\"dark\"")))
                .andExpect(content().string(containsString("mask-tile-dark.png")))
                .andExpect(content().string(containsString("dark-asset.png")))
                // фон тела переопределяет .bg-light (!important у Bootstrap)
                .andExpect(content().string(containsString("background-color: var(--app-bg) !important")));
    }

    @Test
    void appCssKeepsThemeToggleAbovePageContent() throws Exception {
        // тумблер темы — position:fixed внутри <nav> (z-index:1050). Пока nav создаёт
        // stacking context (z-index:1), а main/footer НЕ имеют z-index (z:auto),
        // контент страницы никогда не ложится поверх кнопки и не съедает клики.
        // Если main/footer получат z-index == 1, карточки перекроют тумблер (баг приёмки).
        mockMvc.perform(get("/css/app.css"))
                .andExpect(status().isOk())
                // общий блок: только position:relative, БЕЗ z-index на main/footer
                .andExpect(content().string(containsString(".navbar,\nmain,\nfooter {\n    position: relative;\n}")))
                // z-index остаётся только у navbar — кнопка поверх контента
                .andExpect(content().string(containsString(".navbar {\n    z-index: 1;\n}")))
                .andExpect(content().string(containsString(".theme-toggle {\n    position: fixed;")))
                .andExpect(content().string(containsString("z-index: 1050;")));
    }

    @Test
    void appCssHasScrollBottomButtonStyle() throws Exception {
        mockMvc.perform(get("/css/app.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(".scroll-bottom-btn")));
    }
}
