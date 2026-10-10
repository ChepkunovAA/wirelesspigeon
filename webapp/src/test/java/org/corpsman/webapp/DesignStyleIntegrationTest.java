package org.corpsman.webapp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void appCssDefinesCardTextColorsForBothThemes() throws Exception {
        // Текст карточек (описания, статусы) задаётся переменными темы: иначе
        // в тёмной теме он сливается с фоном карточки.
        mockMvc.perform(get("/css/app.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("--app-card-text:")))
                .andExpect(content().string(containsString("--app-card-muted:")))
                .andExpect(content().string(containsString("var(--app-card-muted)")));
    }

@Test
    void appCssDefinesDarkGameRowColorsPerTheme() throws Exception {
        // Фон строк списка игр задаётся переменными темы: в тёмной теме
        // «набор игроков» и «идёт игра» — тёмные зелёный и синий,
        // «завершена» — чёрный, в светлой — белый.
        mockMvc.perform(get("/css/app.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("--app-game-recruiting:")))
                .andExpect(content().string(containsString("--app-game-active:")))
                .andExpect(content().string(containsString("--app-game-closed:")))
                .andExpect(content().string(containsString(".game-row--recruiting {")))
                .andExpect(content().string(containsString(".game-row--active {")))
                .andExpect(content().string(containsString(".game-row--closed {")));
    }

    @Test
    void closedGamesUseCardSurfaceInDarkTheme() throws Exception {
        // Завершённые игры в тёмной теме — оттенок карточки форума: тот же
        // --app-surface, которым красятся карточки.
        String css = mockMvc.perform(get("/css/app.css"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(darkVariable(css, "--app-game-closed"))
                .as("цвет завершённых игр в тёмной теме")
                .isEqualTo(darkVariable(css, "--app-surface"));
    }

    /** Значение переменной в блоке тёмной темы. */
    private String darkVariable(String css, String name) {
        int dark = css.indexOf("html[data-bs-theme=\"dark\"] {");
        assertThat(dark).as("блок тёмной темы").isPositive();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?m)^\\s*" + java.util.regex.Pattern.quote(name) + ":\\s*([^;]+);")
                .matcher(css.substring(dark));
        assertThat(matcher.find()).as("переменная %s в тёмной теме", name).isTrue();
        return matcher.group(1).trim();
    }

    @Test
    void gameRowColorIsSetThroughBootstrapTableVariable() throws Exception {
        // Bootstrap красит фон ячеек таблицы через --bs-table-bg, поэтому цвет
        // строки задаётся этой переменной: иначе ячейки закрашивают фон строки
        // и все строки выглядят одинаковыми.
        String css = mockMvc.perform(get("/css/app.css"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        for (String rowClass : List.of("game-row--recruiting", "game-row--active", "game-row--closed")) {
            int start = css.indexOf("." + rowClass + " {");
            assertThat(start).as("класс %s", rowClass).isPositive();
            String rule = css.substring(start, css.indexOf('}', start));
            assertThat(rule).as("правило %s", rowClass)
                    .contains("--bs-table-bg: var(--app-game-")
                    .doesNotContain("background-color:");
        }
    }

    @Test
    void renderedPagesHaveNoUnprocessedInlineJs() throws Exception {
        // Thymeleaf вырезает /*[[...]]*/ вместе с окружающим кодом, поэтому
        // два таких подстановки в одной строке JS дают синтаксическую ошибку и
        // страница теряет весь клиентский скрипт. Необработанная подстановка на
        // странице — признак того, что скрипт не был разобран.
        for (String path : List.of("/", "/rpg/games", "/login")) {
            String page = mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            assertThat(page).as(path).doesNotContain("/*[[");
        }
    }

    @Test
    void appCssAlignsMoneySteppersInOneColumn() throws Exception {
        // Кнопки ± у всех разрядов монет одной ширины — иначе числа в строках
        // разъезжаются.
        mockMvc.perform(get("/css/app.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(".money-step {")))
                .andExpect(content().string(containsString(".money-convert {")));
    }

    @Test
    void rpgTemplatesHaveNoHardcodedLightTextOnCards() throws Exception {
        // В шаблонах листа и системы нет захардкоженных светлых цветов текста:
        // описание элемента и шеврон переезжают на переменные темы, иначе в
        // тёмной теме карточки нечитаемы.
        for (String template : List.of("rpg/character.html", "rpg/system-form.html", "rpg/apply.html")) {
            String html = new String(Objects.requireNonNull(getClass().getClassLoader()
                    .getResourceAsStream("templates/" + template)).readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            assertThat(html).as(template).doesNotContain("color: #495057").doesNotContain("color: #6c757d");
        }
    }
}
