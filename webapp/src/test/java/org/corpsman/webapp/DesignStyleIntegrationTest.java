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
}
