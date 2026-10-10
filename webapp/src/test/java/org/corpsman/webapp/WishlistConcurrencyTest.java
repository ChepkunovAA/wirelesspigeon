package org.corpsman.webapp;

import org.corpsman.auth.model.User;
import org.corpsman.auth.model.UserRole;
import org.corpsman.auth.repository.UserRepository;
import org.corpsman.rpg.dto.SheetForm;
import org.corpsman.rpg.model.Game;
import org.corpsman.rpg.model.GameStatus;
import org.corpsman.rpg.model.GameSystem;
import org.corpsman.rpg.model.GameVisibility;
import org.corpsman.rpg.model.SheetAttribute;
import org.corpsman.rpg.repository.GameRepository;
import org.corpsman.rpg.repository.SheetAttributeRepository;
import org.corpsman.rpg.service.CharacterSheetService;
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
 * Проверяет, что конкурентные запросы не приводят к ошибкам и не нарушают
 * лимиты: резервирование подарков и правку денег на листе.
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

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private SheetAttributeRepository attributeRepository;

    @Autowired
    private CharacterSheetService characterSheetService;

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

    /**
     * Параллельные правки денег на одном листе не теряются и не падают.
     * <p>
     * Разряд — это «прочитал, прибавил, записал»: обычная запись через JPA при
     * одновременных нажатиях давала конфликт версии строки и ответ 500, поэтому
     * сумма меняется атомарными UPDATE в базе
     * ({@code SheetAttributeRepository.addMoney}, {@code spendMoney}).
     * <p>
     * Проверка smoke-уровня: воспроизвести конфликт версии детерминированно
     * нельзя (нужны две транзакции, читающие до записи друг друга), поэтому
     * тест фиксирует инвариант суммы и отсутствие ошибок при гонке.
     */
    @Test
    void concurrentMoneyChangesDoNotFail() throws Exception {
        User owner = createUser("auto_conc_rpg_owner");
        Game game = new Game();
        game.setTitle("Гонка монет");
        game.setDescription("Параллельные правки денег.");
        game.setMasterId(owner.getId());
        game.setSystem(GameSystem.DND);
        game.setVisibility(GameVisibility.OPEN);
        game.setStatus(GameStatus.ACTIVE);
        game = gameRepository.save(game);

        SheetForm form = new SheetForm();
        form.setName("Гонщик");
        var sheet = characterSheetService.create(game, owner.getId(), true, form, null);

        int threads = 8;
        int rounds = 5;
        List<Callable<Void>> moneyTasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            boolean addsGold = i % 2 == 0;
            moneyTasks.add(() -> {
                for (int round = 0; round < rounds; round++) {
                    if (addsGold) {
                        characterSheetService.changeMoney(sheet, "gold", 1);
                    } else {
                        characterSheetService.changeMoney(sheet, "silver", 1);
                        characterSheetService.convertMoney(sheet, "gold", "down");
                    }
                }
                return null;
            });
        }

        ExecutorService moneyExecutor = Executors.newFixedThreadPool(threads);
        try {
            for (Future<Void> future : moneyExecutor.invokeAll(moneyTasks)) {
                // Конфликт версии строки пришёл бы исключением из future.get().
                future.get();
            }
        } finally {
            moneyExecutor.shutdown();
        }

        int gold = money(sheet.getId(), "money:gold");
        int silver = money(sheet.getId(), "money:silver");
        assertThat(gold).isNotNegative();
        assertThat(silver).isNotNegative();
        // Ничего не потеряно и не задваировано: обмен сохраняет сумму, поэтому
        // золото (×10) и серебро вместе дают ровно то, что нажали:
        // половина потоков добавила по 5 золотых, половина — по 5 серебряных.
        assertThat(gold * 10 + silver).isEqualTo((threads / 2) * rounds * 10 + (threads / 2) * rounds);
    }

    private int money(Long sheetId, String key) {
        return attributeRepository.findBySheetIdAndKey(sheetId, key)
                .map(SheetAttribute::getNumericValue)
                .orElse(0);
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