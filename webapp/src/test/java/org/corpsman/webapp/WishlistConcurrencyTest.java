package org.corpsman.webapp;

import org.corpsman.auth.model.User;
import org.corpsman.auth.model.UserRole;
import org.corpsman.auth.repository.UserRepository;
import org.corpsman.common.exception.BusinessException;
import org.corpsman.wishlist.model.Gift;
import org.corpsman.wishlist.repository.GiftRepository;
import org.corpsman.wishlist.repository.GiftReservationRepository;
import org.corpsman.wishlist.service.WishlistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверяет, что лимит резервирований не нарушается при конкурентных запросах.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WishlistConcurrencyTest {

    private static final int LIMIT = 5;
    private static final int ATTEMPTS = 20;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GiftRepository giftRepository;

    @Autowired
    private GiftReservationRepository reservationRepository;

    @Autowired
    private WishlistService wishlistService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void concurrentReservationsNeverExceedLimit() throws Exception {
        User owner = createUser("auto_conc_owner");
        Gift gift = new Gift();
        gift.setTitle("Редкий подарок");
        gift.setDescription("");
        gift.setOwnerId(owner.getId());
        gift.setLimit(LIMIT);
        gift.setReservedCount(0);
        giftRepository.save(gift);

        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < ATTEMPTS; i++) {
            final long userId = createUser("auto_conc_user_" + i).getId();
            tasks.add(() -> {
                try {
                    wishlistService.reserveGift(gift.getId(), userId);
                    return true;
                } catch (BusinessException ex) {
                    return false;
                }
            });
        }

        ExecutorService executor = Executors.newFixedThreadPool(ATTEMPTS);
        List<Future<Boolean>> futures;
        try {
            futures = executor.invokeAll(tasks);
        } finally {
            executor.shutdown();
        }

        long success = futures.stream().filter(f -> {
            try {
                return f.get();
            } catch (Exception e) {
                return false;
            }
        }).count();

        assertThat(success).isEqualTo(LIMIT);
        assertThat(giftRepository.findById(gift.getId()).orElseThrow().getReservedCount())
                .isEqualTo(LIMIT);
        assertThat(reservationRepository.findByGiftId(gift.getId())).hasSize(LIMIT);
    }

    private User createUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setRole(UserRole.RESIDENT);
        user.setActive(true);
        return userRepository.save(user);
    }
}