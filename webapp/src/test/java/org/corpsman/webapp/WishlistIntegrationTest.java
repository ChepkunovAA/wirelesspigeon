package org.corpsman.webapp;

import org.corpsman.auth.model.User;
import org.corpsman.auth.model.UserRole;
import org.corpsman.auth.repository.UserRepository;
import org.corpsman.wishlist.model.Gift;
import org.corpsman.wishlist.repository.GiftRepository;
import org.corpsman.wishlist.repository.GiftReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class WishlistIntegrationTest {

    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GiftRepository giftRepository;

    @Autowired
    private GiftReservationRepository reservationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void clearCaches() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @Test
    void ownerSeesGiftsInOwnList() throws Exception {
        User alice = createUser("wish_alice_1");
        addGift(alice, "Книга", 2);
        addGift(alice, "Кофе", 0);
        MockHttpSession session = login("wish_alice_1");

        mockMvc.perform(get("/gifts").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Мой вишлист")))
                .andExpect(content().string(containsString("Книга")))
                .andExpect(content().string(containsString("Кофе")));
    }

    @Test
    void addGiftRedirectsToOwnList() throws Exception {
        createUser("wish_alice_2");
        MockHttpSession session = login("wish_alice_2");

        mockMvc.perform(post("/gifts/add").session(session).with(csrf())
                        .param("title", "Велосипед")
                        .param("description", "Городской, рама 54")
                        .param("limit", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/gifts"));

        assertThat(giftRepository.count()).isEqualTo(1);
    }

    @Test
    void addGiftRejectsTooShortTitle() throws Exception {
        createUser("wish_alice_3");
        MockHttpSession session = login("wish_alice_3");

        mockMvc.perform(post("/gifts/add").session(session).with(csrf())
                        .param("title", "К")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Название должно быть от 2 до 200 символов")));

        assertThat(giftRepository.count()).isZero();
    }

    @Test
    void addGiftRejectsNegativeLimit() throws Exception {
        createUser("wish_alice_4");
        MockHttpSession session = login("wish_alice_4");

        mockMvc.perform(post("/gifts/add").session(session).with(csrf())
                        .param("title", "Настольная игра")
                        .param("limit", "-1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Лимит не может быть отрицательным")));
    }

    @Test
    void ownerDeletesGiftAndCascadesReservations() throws Exception {
        User alice = createUser("wish_alice_5");
        User bob = createUser("wish_bob_5");
        Gift gift = addGift(alice, "Пылесос", 2);
        reserve(gift, bob);
        MockHttpSession session = login("wish_alice_5");

        mockMvc.perform(post("/gifts/delete/" + gift.getId()).session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/gifts"));

        assertThat(giftRepository.findById(gift.getId())).isEmpty();
        assertThat(reservationRepository.findByGiftId(gift.getId())).isEmpty();
    }

    @Test
    void nonOwnerCannotDeleteGift() throws Exception {
        User alice = createUser("wish_alice_6");
        User bob = createUser("wish_bob_6");
        Gift gift = addGift(alice, "Телефон", 1);
        MockHttpSession session = login("wish_bob_6");

        mockMvc.perform(post("/gifts/delete/" + gift.getId()).session(session).with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(giftRepository.findById(gift.getId())).isPresent();
    }

    @Test
    void loweringLimitWithoutConfirmationShowsWarning() throws Exception {
        User alice = createUser("wish_alice_7");
        Gift gift = addGift(alice, "Кресло", 3);
        gift.setReservedCount(3);
        giftRepository.save(gift);
        MockHttpSession session = login("wish_alice_7");

        mockMvc.perform(post("/gifts/edit/" + gift.getId()).session(session).with(csrf())
                        .param("title", "Кресло")
                        .param("description", "")
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Новый лимит (1) меньше текущего количества резервов (3)")));

        Gift updated = giftRepository.findById(gift.getId()).orElseThrow();
        assertThat(updated.getLimit()).isEqualTo(3);
    }

    @Test
    void loweringLimitWithConfirmationCancelsLatestReservations() throws Exception {
        User alice = createUser("wish_alice_8");
        User bob = createUser("wish_bob_8");
        User carol = createUser("wish_carol_8");
        Gift gift = addGift(alice, "Гитара", 3);
        reserve(gift, bob);
        reserve(gift, carol);
        gift.setReservedCount(2);
        giftRepository.save(gift);
        MockHttpSession session = login("wish_alice_8");

        mockMvc.perform(post("/gifts/edit/" + gift.getId()).session(session).with(csrf())
                        .param("title", "Гитара")
                        .param("description", "")
                        .param("limit", "1")
                        .param("confirmExcess", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/gifts"));

        Gift updated = giftRepository.findById(gift.getId()).orElseThrow();
        assertThat(updated.getLimit()).isEqualTo(1);
        assertThat(updated.getReservedCount()).isEqualTo(1);
        assertThat(reservationRepository.findByGiftId(gift.getId())).hasSize(1);
    }

    @Test
    void nonOwnerCannotEditGift() throws Exception {
        User alice = createUser("wish_alice_9");
        User bob = createUser("wish_bob_9");
        Gift gift = addGift(alice, "Лыжи", 1);
        MockHttpSession session = login("wish_bob_9");

        mockMvc.perform(post("/gifts/edit/" + gift.getId()).session(session).with(csrf())
                        .param("title", "Лыжи")
                        .param("description", "")
                        .param("limit", "2"))
                .andExpect(status().isForbidden());
    }

    @Test
    void usersPageShowsOwnersAndSearchWorks() throws Exception {
        User alice = createUser("wish_alice_10");
        User bob = createUser("wish_bob_10");
        addGift(alice, "Чайник", 1);
        addGift(bob, "Часы", 1);
        MockHttpSession session = login("wish_bob_10");

        mockMvc.perform(get("/gifts/users").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("wish_alice_10")))
                .andExpect(content().string(containsString("/gifts/user/" + bob.getId())));

        mockMvc.perform(get("/gifts/users").param("search", "alice").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("wish_alice_10")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("/gifts/user/" + bob.getId()))));
    }

    @Test
    void usersWithoutGiftsDoNotAppear() throws Exception {
        User alice = createUser("wish_alice_11");
        User idle = createUser("wish_idle_11");
        addGift(alice, "Ковёр", 1);
        MockHttpSession session = login("wish_alice_11");

        mockMvc.perform(get("/gifts/users").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("wish_alice_11")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("wish_idle_11"))));
    }

    @Test
    void viewingOwnUserRedirectsToOwnList() throws Exception {
        User alice = createUser("wish_alice_12");
        addGift(alice, "Фонарь", 1);
        MockHttpSession session = login("wish_alice_12");

        mockMvc.perform(get("/gifts/user/" + alice.getId()).session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/gifts"));
    }

    @Test
    void visitorSeesAnotherUsersGifts() throws Exception {
        User alice = createUser("wish_alice_13");
        User bob = createUser("wish_bob_13");
        addGift(alice, "Микрофон", 2);
        MockHttpSession session = login("wish_bob_13");

        mockMvc.perform(get("/gifts/user/" + alice.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Микрофон")))
                .andExpect(content().string(containsString("wish_alice_13")));
    }

    @Test
    void reserveGiftSucceeds() throws Exception {
        User alice = createUser("wish_alice_14");
        User bob = createUser("wish_bob_14");
        Gift gift = addGift(alice, "Ноутбук", 2);
        MockHttpSession session = login("wish_bob_14");

        mockMvc.perform(post("/gifts/reserve/" + gift.getId()).session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.reservedByMe").value(true))
                .andExpect(jsonPath("$.newReservedCount").value(1));

        assertThat(reservationRepository.existsByGiftIdAndUserId(gift.getId(), bob.getId())).isTrue();
        assertThat(giftRepository.findById(gift.getId()).orElseThrow().getReservedCount()).isEqualTo(1);
    }

    @Test
    void cannotReserveOwnGift() throws Exception {
        User alice = createUser("wish_alice_15");
        Gift gift = addGift(alice, "Сумка", 1);
        MockHttpSession session = login("wish_alice_15");

        mockMvc.perform(post("/gifts/reserve/" + gift.getId()).session(session).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("Нельзя зарезервировать свой подарок")));
    }

    @Test
    void cannotReserveTwice() throws Exception {
        User alice = createUser("wish_alice_16");
        User bob = createUser("wish_bob_16");
        Gift gift = addGift(alice, "Фотоаппарат", 2);
        reserve(gift, bob);
        MockHttpSession session = login("wish_bob_16");

        mockMvc.perform(post("/gifts/reserve/" + gift.getId()).session(session).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("Вы уже зарезервировали")));
    }

    @Test
    void cannotReserveWhenLimitReached() throws Exception {
        User alice = createUser("wish_alice_17");
        User bob = createUser("wish_bob_17");
        User carol = createUser("wish_carol_17");
        Gift gift = addGift(alice, "Проектор", 1);
        reserve(gift, bob);
        MockHttpSession session = login("wish_carol_17");

        mockMvc.perform(post("/gifts/reserve/" + gift.getId()).session(session).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("Лимит резервирования достигнут")));
    }

    @Test
    void unreserveGiftSucceeds() throws Exception {
        User alice = createUser("wish_alice_18");
        User bob = createUser("wish_bob_18");
        Gift gift = addGift(alice, "Наушники", 3);
        reserve(gift, bob);
        MockHttpSession session = login("wish_bob_18");

        mockMvc.perform(post("/gifts/unreserve/" + gift.getId()).session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.reservedByMe").value(false))
                .andExpect(jsonPath("$.newReservedCount").value(0));

        assertThat(reservationRepository.existsByGiftIdAndUserId(gift.getId(), bob.getId())).isFalse();
        assertThat(giftRepository.findById(gift.getId()).orElseThrow().getReservedCount()).isZero();
    }

    @Test
    void unreserveWithoutReservationFails() throws Exception {
        User alice = createUser("wish_alice_19");
        User bob = createUser("wish_bob_19");
        Gift gift = addGift(alice, "Клавиатура", 1);
        MockHttpSession session = login("wish_bob_19");

        mockMvc.perform(post("/gifts/unreserve/" + gift.getId()).session(session).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("Резерв не найден")));
    }

    @Test
    void publicProfileShowsBriefWishlistWithReservationData() throws Exception {
        User alice = createUser("wish_alice_20");
        User bob = createUser("wish_bob_20");
        Gift gift = addGift(alice, "Электронная книга", 2);
        reserve(gift, bob);
        MockHttpSession session = login("wish_bob_20");

        mockMvc.perform(get("/profile/" + alice.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Электронная книга")))
                .andExpect(content().string(containsString("/gifts/user/" + alice.getId())))
                .andExpect(content().string(containsString("Вишлист")))
                .andExpect(content().string(containsString("Ваша бронь")));
    }

    @Test
    void ownerDoesNotSeeReservationDetailsInOwnList() throws Exception {
        User alice = createUser("wish_alice_21");
        User bob = createUser("wish_bob_21");
        Gift gift = addGift(alice, "Кофеварка", 2);
        reserve(gift, bob);
        MockHttpSession session = login("wish_alice_21");

        mockMvc.perform(get("/gifts").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Кофеварка")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("wish_bob_21"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Зарезервировали"))));
    }

    @Test
    void usersPageHasClickableUsernames() throws Exception {
        User alice = createUser("wish_alice_22");
        addGift(alice, "Фонарик", 1);
        MockHttpSession session = login("wish_alice_22");

        mockMvc.perform(get("/gifts/users").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/profile/" + alice.getId())))
                .andExpect(content().string(containsString("/gifts/user/" + alice.getId())));
    }

    @Test
    void giftPagesRequireAuthentication() throws Exception {
        mockMvc.perform(get("/gifts"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/gifts/reserve/1").with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    private User createUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(UserRole.USER);
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

    private Gift addGift(User owner, String title, int limit) {
        Gift gift = new Gift();
        gift.setTitle(title);
        gift.setDescription("");
        gift.setOwnerId(owner.getId());
        gift.setLimit(limit);
        gift.setReservedCount(0);
        return giftRepository.save(gift);
    }

    private void reserve(Gift gift, User user) {
        org.corpsman.wishlist.model.GiftReservation reservation =
                new org.corpsman.wishlist.model.GiftReservation();
        reservation.setGiftId(gift.getId());
        reservation.setUserId(user.getId());
        reservation.setReservedAt(LocalDateTime.now().plusSeconds(user.getId()));
        reservationRepository.save(reservation);
        gift.setReservedCount(gift.getReservedCount() + 1);
        giftRepository.save(gift);
    }
}