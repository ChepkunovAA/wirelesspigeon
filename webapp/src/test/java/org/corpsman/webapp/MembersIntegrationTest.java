package org.corpsman.webapp;

import org.corpsman.auth.model.User;
import org.corpsman.auth.model.UserRole;
import org.corpsman.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
class MembersIntegrationTest {

    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void membersPageShowsUsernameRoleAndLastLogin() throws Exception {
        User alice = createUser("auto_members_alice_1", UserRole.RESIDENT);
        createUser("auto_members_admin_1", UserRole.MASTER);
        MockHttpSession session = login("auto_members_alice_1");

        String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"));

        mockMvc.perform(get("/users").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Участники")))
                .andExpect(content().string(containsString("auto_members_alice_1")))
                .andExpect(content().string(containsString("/profile/" + alice.getId())))
                .andExpect(content().string(containsString("MASTER")))
                .andExpect(content().string(containsString(today)))
                .andExpect(content().string(containsString("<span>—</span>")));
    }

    @Test
    void membersPageSearchesByUsername() throws Exception {
        createUser("auto_members_boris_2", UserRole.RESIDENT);
        User alice = createUser("auto_members_alice_2", UserRole.RESIDENT);
        MockHttpSession session = login("auto_members_alice_2");

        mockMvc.perform(get("/users").session(session).param("search", "boris"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("auto_members_boris_2")))
                .andExpect(content().string(not(containsString("/profile/" + alice.getId()))));
    }

    @Test
    void adminCanBanUserFromMembersPage() throws Exception {
        User admin = createUser("auto_members_admin_3", UserRole.MASTER);
        User victim = createUser("auto_members_victim_3", UserRole.RESIDENT);
        MockHttpSession session = login("auto_members_admin_3");

        mockMvc.perform(post("/users/" + victim.getId() + "/ban").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/users"));

        User banned = userRepository.findById(victim.getId()).orElseThrow();
        assertThat(banned.isActive()).isFalse();

        mockMvc.perform(get("/users").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("auto_members_victim_3"))));
    }

    @Test
    void banButtonVisibleForAdminOnlyOnProfile() throws Exception {
        User admin = createUser("auto_members_admin_4", UserRole.MASTER);
        User target = createUser("auto_members_user_4", UserRole.RESIDENT);
        MockHttpSession adminSession = login("auto_members_admin_4");
        MockHttpSession userSession = login("auto_members_user_4");

        mockMvc.perform(get("/profile/" + target.getId()).session(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/users/" + target.getId() + "/ban")));

        mockMvc.perform(get("/profile/" + admin.getId()).session(userSession))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/users/" + admin.getId() + "/ban"))));
    }

    @Test
    void banRequiresAdminRole() throws Exception {
        User regular = createUser("auto_members_user_5", UserRole.RESIDENT);
        User victim = createUser("auto_members_victim_5", UserRole.RESIDENT);
        MockHttpSession session = login("auto_members_user_5");

        mockMvc.perform(post("/users/" + victim.getId() + "/ban").session(session).with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(userRepository.findById(victim.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void adminCannotBanSelfOrOtherAdmin() throws Exception {
        User chief = createUser("auto_members_chief_6", UserRole.MASTER);
        User deputy = createUser("auto_members_deputy_6", UserRole.MASTER);
        MockHttpSession session = login("auto_members_chief_6");

        mockMvc.perform(post("/users/" + deputy.getId() + "/ban").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/users"));

        assertThat(userRepository.findById(deputy.getId()).orElseThrow().isActive()).isTrue();

        mockMvc.perform(post("/users/" + chief.getId() + "/ban").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/users"));

        assertThat(userRepository.findById(chief.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void bannedUserCannotLoginAndDisappearsFromMembersPage() throws Exception {
        User admin = createUser("auto_members_admin_7", UserRole.MASTER);
        User victim = createUser("auto_members_victim_7", UserRole.RESIDENT);
        MockHttpSession adminSession = login("auto_members_admin_7");

        mockMvc.perform(post("/users/" + victim.getId() + "/ban").session(adminSession).with(csrf()))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/login").with(csrf())
                        .param("username", "auto_members_victim_7")
                        .param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void adminSeesBanButtonForUsersAndBadgesForAdminsAndSelf() throws Exception {
        User chief = createUser("auto_members_chief_8", UserRole.MASTER);
        createUser("auto_members_deputy_8", UserRole.MASTER);
        User regular = createUser("auto_members_user_8", UserRole.RESIDENT);
        MockHttpSession session = login("auto_members_chief_8");

        String page = mockMvc.perform(get("/users").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/users/" + regular.getId() + "/ban")))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(page).contains("Это вы");
        assertThat(page).contains("Бан недоступен");

        int buttons = page.split("/users/" + regular.getId() + "/ban", -1).length - 1;
        assertThat(buttons).isEqualTo(1);
    }

    @Test
    void adminSeesUnavailableBadgeInsteadOfBanButtonOnAdminProfile() throws Exception {
        User chief = createUser("auto_members_chief_9", UserRole.MASTER);
        User deputy = createUser("auto_members_deputy_9", UserRole.MASTER);
        MockHttpSession session = login("auto_members_chief_9");

        String page = mockMvc.perform(get("/profile/" + deputy.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Бан недоступен")))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(page).doesNotContain("/users/" + deputy.getId() + "/ban");
    }

    private User createUser(String username, UserRole role) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(role);
        user.setActive(true);
        return userRepository.save(user);
    }

    private MockHttpSession login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/login").with(csrf())
                        .param("username", username)
                        .param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
