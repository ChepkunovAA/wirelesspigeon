package org.corpsman.webapp;

import org.corpsman.auth.model.User;
import org.corpsman.auth.model.UserRole;
import org.corpsman.auth.repository.UserRepository;
import org.corpsman.rpg.model.ApplicationStatus;
import org.corpsman.rpg.model.DiceRoll;
import org.corpsman.rpg.model.Game;
import org.corpsman.rpg.model.GameStatus;
import org.corpsman.rpg.model.GameSystem;
import org.corpsman.rpg.model.GameVisibility;
import org.corpsman.rpg.model.RoomPost;
import org.corpsman.rpg.model.SavedRoll;
import org.corpsman.rpg.model.SheetAttribute;
import org.corpsman.rpg.repository.CharacterSheetRepository;
import org.corpsman.rpg.repository.DiceRollRepository;
import org.corpsman.rpg.repository.GameApplicationRepository;
import org.corpsman.rpg.repository.GameRepository;
import org.corpsman.rpg.repository.GameWhitelistRepository;
import org.corpsman.rpg.repository.RoomMemberRepository;
import org.corpsman.rpg.repository.RoomPostAttachmentRepository;
import org.corpsman.rpg.repository.RoomPostRepository;
import org.corpsman.rpg.repository.SavedRollRepository;
import org.corpsman.rpg.repository.SheetAttributeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RpgIntegrationTest {

    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private GameApplicationRepository applicationRepository;

    @Autowired
    private CharacterSheetRepository sheetRepository;

    @Autowired
    private SheetAttributeRepository attributeRepository;

    @Autowired
    private DiceRollRepository diceRollRepository;

    @Autowired
    private SavedRollRepository savedRollRepository;

    @Autowired
    private RoomPostRepository roomPostRepository;

    @Autowired
    private RoomPostAttachmentRepository postAttachmentRepository;

    @Autowired
    private GameWhitelistRepository whitelistRepository;

    @Autowired
    private RoomMemberRepository roomMemberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void clearCaches() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @Test
    void rpgPagesRequireAuthenticationForMutations() throws Exception {
        mockMvc.perform(post("/rpg/games").with(csrf())
                        .param("title", "Без логина")
                        .param("description", "Нельзя создавать анонимно."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void residentCreatesGameAndSeesDetail() throws Exception {
        User resident = createUser("rpg_master_1", UserRole.RESIDENT);
        MockHttpSession session = login("rpg_master_1");

        mockMvc.perform(get("/rpg/games/new").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ролевая система")));

        mockMvc.perform(post("/rpg/games").session(session).with(csrf())
                        .param("title", "Путь героя")
                        .param("description", "Поход по подземельям в поисках легендарного артефакта.")
                        .param("system", "VERBAL")
                        .param("visibility", "OPEN")
                        .param("status", "RECRUITING"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/rpg/games/*"));

        Game game = gameRepository.findByMasterIdOrderByCreatedAtDesc(resident.getId()).get(0);
        assertThat(game.getTitle()).isEqualTo("Путь героя");
        assertThat(game.getMasterId()).isEqualTo(resident.getId());

        mockMvc.perform(get("/rpg/games").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Путь героя")))
                .andExpect(content().string(containsString("Ролевые игры")));

        mockMvc.perform(get("/rpg/games/" + game.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Поход по подземельям")))
                .andExpect(content().string(containsString("Редактировать")))
                .andExpect(content().string(containsString("Создать комнату")));
    }

    @Test
    void masterCanEditGame() throws Exception {
        User master = createUser("rpg_master_2", UserRole.RESIDENT);
        MockHttpSession session = login("rpg_master_2");
        Game game = createGame(master, "До", "Описание.", GameSystem.VERBAL, GameVisibility.OPEN, GameStatus.RECRUITING);

        mockMvc.perform(get("/rpg/games/" + game.getId() + "/edit").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("До")));

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/edit").session(session).with(csrf())
                        .param("title", "После")
                        .param("description", "Новое описание.")
                        .param("system", "DND")
                        .param("visibility", "GUEST")
                        .param("status", "ACTIVE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/rpg/games/" + game.getId()));

        Game saved = gameRepository.findById(game.getId()).orElseThrow();
        assertThat(saved.getTitle()).isEqualTo("После");
        assertThat(saved.getSystem()).isEqualTo(GameSystem.DND);
        assertThat(saved.getVisibility()).isEqualTo(GameVisibility.GUEST);
        assertThat(saved.getStatus()).isEqualTo(GameStatus.ACTIVE);

        mockMvc.perform(get("/rpg/games/" + game.getId()).session(session))
                .andExpect(content().string(containsString("После")))
                .andExpect(content().string(containsString("Новое описание")));
    }

    @Test
    void guestPostingPublicVisibilityGetsDowngradedToGuest() throws Exception {
        createUser("rpg_guest_1", UserRole.GUEST);
        MockHttpSession session = login("rpg_guest_1");

        mockMvc.perform(post("/rpg/games").session(session).with(csrf())
                        .param("title", "Публичная игра")
                        .param("description", "Описание.")
                        .param("visibility", "PUBLIC"))
                .andExpect(status().is3xxRedirection());

        Game game = gameRepository.findByMasterIdOrderByCreatedAtDesc(
                userRepository.findByUsername("rpg_guest_1").orElseThrow().getId()).get(0);
        assertThat(game.getVisibility()).isEqualTo(GameVisibility.GUEST);
    }

    @Test
    void guestSeesPublicButNotOpenGames() throws Exception {
        User host = createUser("rpg_host_2", UserRole.RESIDENT);
        Game publicGame = createGame(host, "Общая", "Для всех.", GameSystem.VERBAL, GameVisibility.PUBLIC, GameStatus.RECRUITING);
        Game openGame = createGame(host, "Своя", "Для резидентов.", GameSystem.VERBAL, GameVisibility.OPEN, GameStatus.RECRUITING);
        createUser("rpg_guest_2", UserRole.GUEST);
        MockHttpSession session = login("rpg_guest_2");

        mockMvc.perform(get("/rpg/games").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Общая")))
                .andExpect(content().string(not(containsString("Своя"))));

        mockMvc.perform(get("/rpg/games/" + publicGame.getId()).session(session))
                .andExpect(status().isOk());
        mockMvc.perform(get("/rpg/games/" + openGame.getId()).session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousSeesPublicGamesOnly() throws Exception {
        User host = createUser("rpg_host_anon", UserRole.RESIDENT);
        Game publicGame = createGame(host, "Открытая анониму", "Для всех.", GameSystem.VERBAL,
                GameVisibility.PUBLIC, GameStatus.RECRUITING);
        Game openGame = createGame(host, "Своя анониму", "Только резидентам.", GameSystem.VERBAL,
                GameVisibility.OPEN, GameStatus.RECRUITING);
        Game closedGame = createGame(host, "Тайная анониму", "Закрытая.", GameSystem.VERBAL,
                GameVisibility.CLOSED, GameStatus.RECRUITING);

        mockMvc.perform(get("/rpg/games"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Открытая анониму")))
                .andExpect(content().string(not(containsString("Своя анониму"))))
                .andExpect(content().string(not(containsString("Тайная анониму"))))
                .andExpect(content().string(not(containsString("Создать игру"))));

        mockMvc.perform(get("/rpg/games/" + publicGame.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Открытая анониму")))
                .andExpect(content().string(not(containsString("Хотите участвовать?"))));

        mockMvc.perform(get("/rpg/games/" + openGame.getId()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/rpg/games/" + closedGame.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    void guestCanCreateAndManageOwnGame() throws Exception {
        createUser("rpg_guest_3", UserRole.GUEST);
        MockHttpSession session = login("rpg_guest_3");

        mockMvc.perform(post("/rpg/games").session(session).with(csrf())
                        .param("title", "Игра гостя")
                        .param("description", "Гостевой стол.")
                        .param("visibility", "GUEST")
                        .param("status", "RECRUITING"))
                .andExpect(status().is3xxRedirection());

        Game game = gameRepository.findByMasterIdOrderByCreatedAtDesc(
                userRepository.findByUsername("rpg_guest_3").orElseThrow().getId()).get(0);

        mockMvc.perform(get("/rpg/games/" + game.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Игра гостя")))
                .andExpect(content().string(containsString("Редактировать")));
    }

    @Test
    void closedGameHiddenUntilWhitelisted() throws Exception {
        User master = createUser("rpg_master_3", UserRole.RESIDENT);
        User resident = createUser("rpg_res_1", UserRole.RESIDENT);
        Game game = createGame(master, "Тайный клуб", "Только по приглашению.", GameSystem.VERBAL, GameVisibility.CLOSED, GameStatus.RECRUITING);
        MockHttpSession resSession = login("rpg_res_1");

        mockMvc.perform(get("/rpg/games").session(resSession))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Тайный клуб"))));
        mockMvc.perform(get("/rpg/games/" + game.getId()).session(resSession))
                .andExpect(status().isForbidden());

        MockHttpSession masterSession = login("rpg_master_3");
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/whitelist/add").session(masterSession).with(csrf())
                        .param("userId", String.valueOf(resident.getId())))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/rpg/games").session(resSession))
                .andExpect(content().string(containsString("Тайный клуб")));
        mockMvc.perform(get("/rpg/games/" + game.getId()).session(resSession))
                .andExpect(status().isOk());
    }

    @Test
    void applyThenRejectFlow() throws Exception {
        User master = createUser("rpg_master_4", UserRole.RESIDENT);
        User player = createUser("rpg_player_4", UserRole.RESIDENT);
        Game game = createGame(master, "Набор", "Ищем двоих.", GameSystem.VERBAL, GameVisibility.OPEN, GameStatus.RECRUITING);
        MockHttpSession session = login("rpg_player_4");

        mockMvc.perform(get("/rpg/games/" + game.getId() + "/apply").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Лист персонажа и заявка")));

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/apply").session(session).with(csrf())
                        .param("name", "Эления")
                        .param("kventa", "Бывший стражник, ищущая приключений.")
                        .param("attributes[0].key", "inventory")
                        .param("attributes[0].listText", "Факел\nХлеб"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/rpg/games/" + game.getId()));

        mockMvc.perform(get("/rpg/games/" + game.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Заявка на рассмотрении")));

        var app = applicationRepository.findByGameIdAndUserId(game.getId(), player.getId()).orElseThrow();
        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.PENDING);
        var sheet = sheetRepository.findById(app.getSheetId()).orElseThrow();
        assertThat(sheet.getName()).isEqualTo("Эления");

        MockHttpSession masterSession = login("rpg_master_4");
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/applications/" + app.getId() + "/reject")
                        .session(masterSession).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/rpg/games/" + game.getId()));

        assertThat(applicationRepository.findByGameIdAndUserId(game.getId(), player.getId()).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.REJECTED);

        mockMvc.perform(get("/rpg/games/" + game.getId()).session(session))
                .andExpect(content().string(containsString("Заявка отклонена")));
    }

    @Test
    void dndSheetFormSpinnersCirclesAndDefaults() throws Exception {
        User master = createUser("rpg_master_dndui", UserRole.RESIDENT);
        User player = createUser("rpg_player_dndui", UserRole.RESIDENT);
        Game game = createGame(master, "DND-UI-тест", "Проверка формы листа.", GameSystem.DND, GameVisibility.OPEN, GameStatus.RECRUITING);

        MockHttpSession session = login("rpg_player_dndui");
        MvcResult pageRes = mockMvc.perform(get("/rpg/games/" + game.getId() + "/apply").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Бонус мастерства")))
                .andExpect(content().string(containsString("prof-circle")))
                .andExpect(content().string(containsString("data-flag-max=\"2\"")))
                .andExpect(content().string(containsString("data-flag-max=\"1\"")))
                .andExpect(content().string(containsString("data-num-view=\"level\">1</span>")))
                .andExpect(content().string(containsString("data-num-view=\"ability:strength\">10</span>")))
                .andExpect(content().string(containsString("data-derived-of=\"save:strength\"")))
                .andExpect(content().string(containsString("data-list-cards=\"inventory\"")))
                .andExpect(content().string(not(containsString("type=\"number\""))))
                .andExpect(content().string(containsString("<select")))
                .andExpect(content().string(containsString(">Бард</option>")))
                .andExpect(content().string(containsString(">Эльф</option>")))
                .andExpect(content().string(containsString(">Плут</option>")))
                .andExpect(content().string(containsString("value=\"__other__\"")))
                .andExpect(content().string(containsString("Другой…")))
                .andExpect(content().string(containsString("Другая…")))
                .andReturn();
        String page = pageRes.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(page).contains("Навыки");
        assertThat(page).doesNotContain("Навыки · ");
        assertThat(page.indexOf("Черты")).isLessThan(page.indexOf("Умения"));
        assertThat(page.indexOf("Умения")).isLessThan(page.indexOf("Заклинания"));
        assertThat(page.indexOf("Заклинания")).isLessThan(page.indexOf("Инвентарь"));

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/apply").session(session).with(csrf())
                        .param("name", "Эльфийка")
                        .param("kventa", "Странница дальних дорог."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/rpg/games/" + game.getId()));

        var app = applicationRepository.findByGameIdAndUserId(game.getId(), player.getId()).orElseThrow();
        Map<String, SheetAttribute> attrs = attributeRepository.findBySheetIdOrderByPositionAsc(app.getSheetId()).stream()
                .collect(Collectors.toMap(SheetAttribute::getKey, Function.identity()));
        assertThat(attrs.get("level").getNumericValue()).isEqualTo(1);
        assertThat(attrs.get("ability:strength").getNumericValue()).isEqualTo(10);
        assertThat(attrs.get("save:strength").getNumericValue()).isNull();
        assertThat(attrs.get("skill:acrobatics").getNumericValue()).isNull();
        assertThat(attrs.get("skill:acrobatics").getFlags()).isZero();
    }

    @Test
    void acceptingApplicationMakesPlayerVisibleAndCounted() throws Exception {
        User master = createUser("rpg_master_5", UserRole.RESIDENT);
        User player = createUser("rpg_player_5", UserRole.RESIDENT);
        Game game = createGameFor(master, "Хроники", GameSystem.VERBAL, GameVisibility.OPEN);
        Long playerId = applyFor("rpg_player_5", game, "Торин");

        acceptFor(game, playerId);

        assertThat(applicationRepository.existsByGameIdAndUserIdAndStatus(game.getId(), playerId, ApplicationStatus.ACCEPTED))
                .isTrue();

        MockHttpSession playerSession = login("rpg_player_5");
        mockMvc.perform(get("/rpg/games/" + game.getId()).session(playerSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Вы в игре")))
                .andExpect(content().string(containsString("Редактировать лист")));

        MockHttpSession masterSession = login("rpg_master_5");
        String masterPage = mockMvc.perform(get("/rpg/games/" + game.getId()).session(masterSession))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(masterPage).contains("Торин");
        assertThat(masterPage).contains("Игроки");
    }

    @Test
    void applyingUpdatesExistingSheet() throws Exception {
        User master = createUser("rpg_master_6", UserRole.RESIDENT);
        createUser("rpg_player_6", UserRole.RESIDENT);
        Game game = createGame(master, "Редактирование", "Обновляем лист.", GameSystem.VERBAL, GameVisibility.OPEN, GameStatus.RECRUITING);
        MockHttpSession session = login("rpg_player_6");

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/apply").session(session).with(csrf())
                        .param("name", "Исходное"))
                .andExpect(status().is3xxRedirection());
        Long playerId = userRepository.findByUsername("rpg_player_6").orElseThrow().getId();
        var app = applicationRepository.findByGameIdAndUserId(game.getId(), playerId).orElseThrow();
        Long sheetId = app.getSheetId();

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/apply").session(session).with(csrf())
                        .param("name", "Обновлённое"))
                .andExpect(status().is3xxRedirection());
        assertThat(applicationRepository.existsByGameIdAndUserId(game.getId(), playerId)).isTrue();
        assertThat(sheetRepository.findById(sheetId).orElseThrow().getName()).isEqualTo("Обновлённое");
    }

    @Test
    void masterCannotApplyToOwnGame() throws Exception {
        User master = createUser("rpg_master_7", UserRole.RESIDENT);
        Game game = createGame(master, "Своя игра", "Ведущий не может подавать.", GameSystem.VERBAL, GameVisibility.OPEN, GameStatus.RECRUITING);
        MockHttpSession session = login("rpg_master_7");

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/apply").session(session).with(csrf())
                        .param("name", "Попытка"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Нельзя подать заявку на свою игру")));
    }

    @Test
    void masterCreatesNpcs() throws Exception {
        User master = createUser("rpg_master_8", UserRole.RESIDENT);
        Game game = createGame(master, "NPC-тест", "Нужны NPC.", GameSystem.VERBAL, GameVisibility.OPEN, GameStatus.RECRUITING);
        MockHttpSession session = login("rpg_master_8");

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/npc").session(session).with(csrf())
                        .param("name", "Стражник")
                        .param("kventa", "Немой охранник ворот."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/rpg/games/" + game.getId()));
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/npc").session(session).with(csrf())
                        .param("name", "Трактирщик")
                        .param("kventa", "Хозяин таверны."))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/rpg/games/" + game.getId()));

        var sheets = sheetRepository.findByGameIdAndNpcOrderByCreatedAtDesc(game.getId(), true);
        assertThat(sheets).hasSize(2);
        assertThat(sheets.get(0).isNpc()).isTrue();

        // NPC — не заявки на участие: в списке заявок их быть не должно.
        assertThat(applicationRepository.findByGameIdOrderByCreatedAtDesc(game.getId())).isEmpty();

        mockMvc.perform(get("/rpg/games/" + game.getId()).session(session))
                .andExpect(content().string(containsString("Стражник")))
                .andExpect(content().string(containsString("Трактирщик")))
                .andExpect(content().string(containsString("Заявок пока нет")));
    }

    @Test
    void applicationsListShowsOnlyPending() throws Exception {
        User master = createUser("rpg_master_apl", UserRole.RESIDENT);
        User p1 = createUser("rpg_player_apl1", UserRole.RESIDENT);
        User p2 = createUser("rpg_player_apl2", UserRole.RESIDENT);
        Game game = createGameFor(master, "Заявки-лист", GameSystem.VERBAL, GameVisibility.OPEN);
        applyFor("rpg_player_apl1", game, "Первый");
        applyFor("rpg_player_apl2", game, "Второй");

        MockHttpSession masterSession = login("rpg_master_apl");
        mockMvc.perform(get("/rpg/games/" + game.getId()).session(masterSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Лист: Первый")))
                .andExpect(content().string(containsString("Лист: Второй")));

        Long app1 = applicationRepository.findByGameIdAndUserId(game.getId(), p1.getId()).orElseThrow().getId();
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/applications/" + app1 + "/accept")
                        .session(masterSession).with(csrf()))
                .andExpect(status().is3xxRedirection());

        // Принятая заявка пропадает из списка, нерассмотренная остаётся.
        mockMvc.perform(get("/rpg/games/" + game.getId()).session(masterSession))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Лист: Первый"))))
                .andExpect(content().string(containsString("Лист: Второй")));

        Long app2 = applicationRepository.findByGameIdAndUserId(game.getId(), p2.getId()).orElseThrow().getId();
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/applications/" + app2 + "/reject")
                        .session(masterSession).with(csrf()))
                .andExpect(status().is3xxRedirection());

        // Отклонённая тоже не отображается; у принятого игрока нет маркера NPC.
        String gamePage = mockMvc.perform(get("/rpg/games/" + game.getId()).session(masterSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Заявок пока нет")))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(gamePage).doesNotContain("Лист: Второй");
        assertThat(gamePage).doesNotContain("NPC ведущего");
    }

    @Test
    void postWithRollsAndDiceClamp() throws Exception {
        User master = createUser("rpg_master_9", UserRole.RESIDENT);
        User player = createUser("rpg_player_9", UserRole.RESIDENT);
        Game game = createGameFor(master, "Комнаты-тест", GameSystem.VERBAL, GameVisibility.OPEN);
        applyFor("rpg_player_9", game, "Воин");
        acceptFor(game, player.getId());

        MvcResult roomRes = mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms")
                        .session(login("rpg_master_9")).with(csrf())
                        .param("title", "Таверна")
                        .param("privateRoom", "false"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        Long roomId = Long.parseLong(roomRes.getResponse().getRedirectedUrl().replaceAll(".*rooms/", ""));

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms/" + roomId + "/posts")
                        .session(login("rpg_player_9")).with(csrf())
                        .param("content", "Разведка боем.")
                        .param("die", "d6", "d20")
                        .param("count", "2", "2000")
                        .param("modifier", "1", "0")
                        .param("rollComment", "Атака", "Инциатива"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/rpg/games/" + game.getId() + "/rooms/" + roomId));

        mockMvc.perform(get("/rpg/games/" + game.getId() + "/rooms/" + roomId)
                        .session(login("rpg_player_9")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Разведка боем.")))
                .andExpect(content().string(containsString(":name=\"'die'\"")))
                .andExpect(content().string(containsString("<span>2</span><span>d6</span>")))
                .andExpect(content().string(containsString(">+1</span>")))
                .andExpect(content().string(containsString("Атака")))
                .andExpect(content().string(containsString("Инциатива")));

        List<DiceRoll> rolls = diceRollRepository.findByPostIdOrderByIdAsc(
                roomPostRepository.findByRoomIdOrderByCreatedAtAsc(roomId).get(0).getId());
        assertThat(rolls).hasSize(2);
        DiceRoll d6Roll = rolls.get(0);
        assertThat(d6Roll.getDieSize()).isEqualTo(6);
        assertThat(d6Roll.getCount()).isEqualTo(2);
        assertThat(d6Roll.getModifier()).isEqualTo(1);
        assertThat(d6Roll.getComment()).isEqualTo("Атака");
        assertThat(d6Roll.getTotal()).isBetween(3, 13);
        DiceRoll d20Roll = rolls.get(1);
        assertThat(d20Roll.getDieSize()).isEqualTo(20);
        assertThat(d20Roll.getCount()).isEqualTo(1000);
    }

    @Test
    void strangerCannotEnterOrPostInRoom() throws Exception {
        User master = createUser("rpg_master_10", UserRole.RESIDENT);
        User stranger = createUser("rpg_stranger_1", UserRole.RESIDENT);
        Game game = createGameFor(master, "Закрытые комнаты", GameSystem.VERBAL, GameVisibility.OPEN);

        MvcResult roomRes = mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms")
                        .session(login("rpg_master_10")).with(csrf())
                        .param("title", "Дом кузнеца"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        Long roomId = Long.parseLong(roomRes.getResponse().getRedirectedUrl().replaceAll(".*rooms/", ""));

        MockHttpSession strSession = login("rpg_stranger_1");
        mockMvc.perform(get("/rpg/games/" + game.getId() + "/rooms/" + roomId).session(strSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Дом кузнеца")))
                .andExpect(content().string(containsString("Вы")));
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms/" + roomId + "/posts")
                        .session(strSession).with(csrf())
                        .param("content", "Нелегальный пост"))
                .andExpect(status().isForbidden());
    }

    @Test
    void privateRoomGatedByMembership() throws Exception {
        User master = createUser("rpg_master_11", UserRole.RESIDENT);
        User player = createUser("rpg_player_11", UserRole.RESIDENT);
        Game game = createGameFor(master, "Приватные", GameSystem.VERBAL, GameVisibility.OPEN);
        applyFor("rpg_player_11", game, "Наблюдатель");
        acceptFor(game, player.getId());

        MvcResult roomRes = mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms")
                        .session(login("rpg_master_11")).with(csrf())
                        .param("title", "Тайная комната")
                        .param("privateRoom", "true"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        Long roomId = Long.parseLong(roomRes.getResponse().getRedirectedUrl().replaceAll(".*rooms/", ""));

        MockHttpSession pSession = login("rpg_player_11");
        String gamePage = mockMvc.perform(get("/rpg/games/" + game.getId()).session(pSession))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(gamePage).contains("Тайная комната");
        assertThat(gamePage).contains("bi-lock-fill");
        assertThat(gamePage).doesNotContain("/rooms/" + roomId);
        mockMvc.perform(get("/rpg/games/" + game.getId() + "/rooms/" + roomId).session(pSession))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms/" + roomId + "/members")
                        .session(login("rpg_master_11")).with(csrf())
                        .param("memberIds", String.valueOf(player.getId())))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/rpg/games/" + game.getId() + "/rooms/" + roomId).session(pSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Тайная комната")));

        String updatedGamePage = mockMvc.perform(get("/rpg/games/" + game.getId()).session(pSession))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(updatedGamePage).contains("Тайная комната");
        assertThat(updatedGamePage).contains("/rooms/" + roomId);
    }

    @Test
    void gameLevelAttributeVisibilityCheckbox() throws Exception {
        User master = createUser("rpg_master_12", UserRole.RESIDENT);
        User player = createUser("rpg_player_12", UserRole.RESIDENT);
        User viewer = createUser("rpg_viewer_12", UserRole.RESIDENT);
        Game game = createGameFor(master, "DND-тест", GameSystem.DND, GameVisibility.OPEN);
        applyFor("rpg_player_12", game, "Варвар");
        Long playerId = userRepository.findByUsername("rpg_player_12").orElseThrow().getId();
        acceptFor(game, playerId);
        var app = applicationRepository.findByGameIdAndUserId(game.getId(), playerId).orElseThrow();
        Long sheetId = app.getSheetId();

        MockHttpSession viewerSession = login("rpg_viewer_12");
        // По умолчанию (attributesVisible=true) все, кто имеет доступ к игре, видят все атрибуты.
        mockMvc.perform(get("/rpg/games/" + game.getId() + "/characters/" + sheetId).session(viewerSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Сила")));

        // Ведущий выключает чекбокс «игроки видят все параметры» в настройках игры.
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/edit").session(login("rpg_master_12")).with(csrf())
                        .param("title", "DND-тест")
                        .param("description", "Описание test.")
                        .param("system", "DND")
                        .param("visibility", "OPEN")
                        .param("status", "RECRUITING")
                        .param("attributesVisible", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/rpg/games/" + game.getId()));

        // Посторонний видит только стандартные поля (имя и квенту) — без атрибутов.
        mockMvc.perform(get("/rpg/games/" + game.getId() + "/characters/" + sheetId).session(viewerSession))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<span>Сила</span>"))))
                .andExpect(content().string(containsString("Ведущий скрыл параметры")));

        // Ведущий и владелец листа видят всё.
        mockMvc.perform(get("/rpg/games/" + game.getId() + "/characters/" + sheetId).session(login("rpg_master_12")))
                .andExpect(content().string(containsString("<span>Сила</span>")));
        mockMvc.perform(get("/rpg/games/" + game.getId() + "/characters/" + sheetId).session(login("rpg_player_12")))
                .andExpect(content().string(containsString("<span>Сила</span>")));
    }

    @Test
    void npcSheetsVisibleToPlayers() throws Exception {
        User master = createUser("rpg_master_n1", UserRole.RESIDENT);
        User player = createUser("rpg_player_n1", UserRole.RESIDENT);
        Game game = createGameFor(master, "NPC-игроки", GameSystem.DND, GameVisibility.OPEN);
        applyFor("rpg_player_n1", game, "Следопыт");
        Long playerId = userRepository.findByUsername("rpg_player_n1").orElseThrow().getId();
        acceptFor(game, playerId);

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/npc").session(login("rpg_master_n1")).with(csrf())
                        .param("name", "Трактирщик Багман")
                        .param("kventa", "Хозяин «Сломанной подковы»."))
                .andExpect(status().is3xxRedirection());
        Long npcId = sheetRepository.findByGameIdAndNpcOrderByCreatedAtDesc(game.getId(), true).get(0).getId();

        MockHttpSession playerSession = login("rpg_player_n1");
        mockMvc.perform(get("/rpg/games/" + game.getId()).session(playerSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Трактирщик Багман")))
                .andExpect(content().string(containsString("/characters/" + npcId)));

        // По умолчанию параметры NPC тоже видны участникам игры.
        mockMvc.perform(get("/rpg/games/" + game.getId() + "/characters/" + npcId).session(playerSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<span>Сила</span>")));
    }

    @Test
    void masterPostsAsNpc() throws Exception {
        User master = createUser("rpg_master_13", UserRole.RESIDENT);
        Game game = createGameFor(master, "NPC-посты", GameSystem.DND, GameVisibility.OPEN);

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/npc").session(login("rpg_master_13")).with(csrf())
                        .param("name", "Стражник Гора")
                        .param("kventa", "Немой страж ворот."))
                .andExpect(status().is3xxRedirection());
        Long npcId = sheetRepository.findByGameIdAndNpcOrderByCreatedAtDesc(game.getId(), true).get(0).getId();

        MvcResult roomRes = mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms")
                        .session(login("rpg_master_13")).with(csrf())
                        .param("title", "Ворота"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        Long roomId = Long.parseLong(roomRes.getResponse().getRedirectedUrl().replaceAll(".*rooms/", ""));

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms/" + roomId + "/posts")
                        .session(login("rpg_master_13")).with(csrf())
                        .param("content", "Кто идёт?")
                        .param("sheetId", String.valueOf(npcId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/rpg/games/" + game.getId() + "/rooms/" + roomId));

        RoomPost post = roomPostRepository.findByRoomIdOrderByCreatedAtAsc(roomId).get(0);
        assertThat(post.getSheetId()).isEqualTo(npcId);

        mockMvc.perform(get("/rpg/games/" + game.getId() + "/rooms/" + roomId).session(login("rpg_master_13")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Кто идёт?")))
                .andExpect(content().string(containsString("Стражник Гора")));
    }

    @Test
    void postWithImageAttachment() throws Exception {
        User master = createUser("rpg_master_14", UserRole.RESIDENT);
        Game game = createGameFor(master, "Вложения", GameSystem.VERBAL, GameVisibility.OPEN);

        MvcResult roomRes = mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms")
                        .session(login("rpg_master_14")).with(csrf())
                        .param("title", "Склеп"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        Long roomId = Long.parseLong(roomRes.getResponse().getRedirectedUrl().replaceAll(".*rooms/", ""));

        MockMultipartFile image = new MockMultipartFile("files", "карта.zip", "image/png", new byte[]{1, 2, 3});
        mockMvc.perform(multipart("/rpg/games/" + game.getId() + "/rooms/" + roomId + "/posts")
                        .file(image)
                        .session(login("rpg_master_14")).with(csrf())
                        .param("content", "Карта склепа"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/rpg/games/" + game.getId() + "/rooms/" + roomId));

        RoomPost post = roomPostRepository.findByRoomIdOrderByCreatedAtAsc(roomId).get(0);
        var attachments = postAttachmentRepository.findByPostIdOrderByIdAsc(post.getId());
        assertThat(attachments).hasSize(1);
        assertThat(attachments.get(0).getContentType()).isEqualTo("image/png");
        assertThat(attachments.get(0).getFileName()).isEqualTo("карта.zip");

        mockMvc.perform(get("/rpg/games/" + game.getId() + "/rooms/" + roomId).session(login("rpg_master_14")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Карта склепа")))
                .andExpect(content().string(containsString("img-thumbnail")))
                .andExpect(content().string(containsString("/attachments/" + attachments.get(0).getId())));

        mockMvc.perform(get("/rpg/games/" + game.getId() + "/rooms/" + roomId
                        + "/attachments/" + attachments.get(0).getId()).session(login("rpg_master_14")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG));
    }

    @Test
    void savedRollScopedToGame() throws Exception {
        User master = createUser("rpg_master_15", UserRole.RESIDENT);
        Game game = createGameFor(master, "Избранное-игра", GameSystem.VERBAL, GameVisibility.OPEN);

        MvcResult roomRes = mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms")
                        .session(login("rpg_master_15")).with(csrf())
                        .param("title", "Комната"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        Long roomId = Long.parseLong(roomRes.getResponse().getRedirectedUrl().replaceAll(".*rooms/", ""));

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms/" + roomId + "/saved-rolls")
                        .session(login("rpg_master_15")).with(csrf())
                        .param("die", "d20")
                        .param("count", "3")
                        .param("modifier", "2")
                        .param("rollComment", "Ambush"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        SavedRoll saved = savedRollRepository.findByUserIdOrderByCreatedAtDesc(master.getId()).get(0);
        assertThat(saved.getGameId()).isEqualTo(game.getId());
        assertThat(saved.getDieSize()).isEqualTo(20);
        assertThat(saved.getComment()).isEqualTo("Ambush");

        mockMvc.perform(get("/rpg/games/" + game.getId() + "/rooms/" + roomId).session(login("rpg_master_15")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ambush")));
    }

    @Test
    void npcFavoriteScopedToSheetAndDeduped() throws Exception {
        User master = createUser("rpg_master_16", UserRole.RESIDENT);
        Game game = createGameFor(master, "Избранное-NPC", GameSystem.DND, GameVisibility.OPEN);

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/npc").session(login("rpg_master_16")).with(csrf())
                        .param("name", "Трактирщик Гобры"))
                .andExpect(status().is3xxRedirection());
        Long npcId = sheetRepository.findByGameIdAndNpcOrderByCreatedAtDesc(game.getId(), true).get(0).getId();

        MvcResult roomRes = mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms")
                        .session(login("rpg_master_16")).with(csrf())
                        .param("title", "Комната"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        Long roomId = Long.parseLong(roomRes.getResponse().getRedirectedUrl().replaceAll(".*rooms/", ""));

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms/" + roomId + "/posts")
                        .session(login("rpg_master_16")).with(csrf())
                        .param("content", "Личное")
                        .param("die", "d20").param("count", "1").param("modifier", "1").param("rollComment", "Удар"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms/" + roomId + "/posts")
                        .session(login("rpg_master_16")).with(csrf())
                        .param("content", "От NPC")
                        .param("sheetId", String.valueOf(npcId))
                        .param("die", "d20").param("count", "1").param("modifier", "2").param("rollComment", "Рык"))
                .andExpect(status().is3xxRedirection());

        List<RoomPost> posts = roomPostRepository.findByRoomIdOrderByCreatedAtAsc(roomId);
        Long ownPostId = posts.stream().filter(p -> "Личное".equals(p.getContent()))
                .findFirst().orElseThrow().getId();
        Long npcPostId = posts.stream().filter(p -> "От NPC".equals(p.getContent()))
                .findFirst().orElseThrow().getId();

        // Свой пост → личное «Избранное» (sheetId is null).
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms/" + roomId + "/saved-rolls")
                        .session(login("rpg_master_16")).with(csrf())
                        .param("postId", String.valueOf(ownPostId))
                        .param("die", "d20").param("count", "1").param("modifier", "1").param("rollComment", "Удар"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        // Пост NPC → вложенное «Избранное» NPC; повторная попытка не дублируется.
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms/" + roomId + "/saved-rolls")
                        .session(login("rpg_master_16")).with(csrf())
                        .param("postId", String.valueOf(npcPostId))
                        .param("die", "d20").param("count", "1").param("modifier", "2").param("rollComment", "Рык"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms/" + roomId + "/saved-rolls")
                        .session(login("rpg_master_16")).with(csrf())
                        .param("postId", String.valueOf(npcPostId))
                        .param("die", "d20").param("count", "1").param("modifier", "2").param("rollComment", "Рык"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        List<SavedRoll> all = savedRollRepository.findByUserIdOrderByCreatedAtDesc(master.getId());
        assertThat(all).hasSize(2);
        SavedRoll own = all.stream().filter(r -> r.getSheetId() == null).findFirst().orElseThrow();
        assertThat(own.getGameId()).isEqualTo(game.getId());
        SavedRoll npc = all.stream().filter(r -> r.getSheetId() != null).findFirst().orElseThrow();
        assertThat(npc.getSheetId()).isEqualTo(npcId);
        assertThat(npc.getComment()).isEqualTo("Рык");

        // Удаление вложенного избранного.
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms/" + roomId
                        + "/saved-rolls/" + npc.getId() + "/delete")
                        .session(login("rpg_master_16")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
        assertThat(savedRollRepository.count()).isEqualTo(1);
    }

    @Test
    void gameCreateOpenWithGuestAccessBecomesGuest() throws Exception {
        User resident = createUser("rpg_master_16", UserRole.RESIDENT);
        MockHttpSession session = login("rpg_master_16");

        mockMvc.perform(post("/rpg/games").session(session).with(csrf())
                        .param("title", "Для всех")
                        .param("description", "Открытая, доступна и гостям.")
                        .param("visibility", "OPEN")
                        .param("guestAccess", "true")
                        .param("status", "RECRUITING"))
                .andExpect(status().is3xxRedirection());

        Game game = gameRepository.findByMasterIdOrderByCreatedAtDesc(resident.getId()).get(0);
        assertThat(game.getVisibility()).isEqualTo(GameVisibility.GUEST);
    }

    @Test
    void gameCreateClosedSavesWhitelistFromForm() throws Exception {
        User master = createUser("rpg_master_17", UserRole.RESIDENT);
        User friend = createUser("rpg_friend_17", UserRole.RESIDENT);
        MockHttpSession session = login("rpg_master_17");

        mockMvc.perform(post("/rpg/games").session(session).with(csrf())
                        .param("title", "Тайный стол")
                        .param("description", "Скрытая игра.")
                        .param("visibility", "CLOSED")
                        .param("whitelistItems", String.valueOf(friend.getId()))
                        .param("status", "RECRUITING"))
                .andExpect(status().is3xxRedirection());

        Game game = gameRepository.findByMasterIdOrderByCreatedAtDesc(master.getId()).get(0);
        assertThat(game.getVisibility()).isEqualTo(GameVisibility.CLOSED);
        assertThat(whitelistRepository.existsByGameIdAndUserId(game.getId(), friend.getId())).isTrue();
    }

    @Test
    void roomCreatePrivateStoresChosenMembers() throws Exception {
        User master = createUser("rpg_master_18", UserRole.RESIDENT);
        Game game = createGameFor(master, "Приват-создание", GameSystem.VERBAL, GameVisibility.OPEN);
        User friend = createUser("rpg_friend_18", UserRole.RESIDENT);

        MvcResult roomRes = mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms")
                        .session(login("rpg_master_18")).with(csrf())
                        .param("title", "Личная")
                        .param("privateRoom", "true")
                        .param("memberIds", String.valueOf(friend.getId())))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        Long roomId = Long.parseLong(roomRes.getResponse().getRedirectedUrl().replaceAll(".*rooms/", ""));

        assertThat(roomMemberRepository.existsByRoomIdAndUserId(roomId, friend.getId())).isTrue();
        assertThat(roomMemberRepository.existsByRoomIdAndUserId(roomId, master.getId())).isFalse();
    }

    @Test
    void postsSignedByAuthorNameNpcNotHighlightedNoBadge() throws Exception {
        User master = createUser("rpg_master_19", UserRole.RESIDENT);
        Game game = createGameFor(master, "Подписи", GameSystem.DND, GameVisibility.OPEN);

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/npc").session(login("rpg_master_19")).with(csrf())
                        .param("name", "Стражник Гора")
                        .param("kventa", "Немой страж ворот."))
                .andExpect(status().is3xxRedirection());
        Long npcId = sheetRepository.findByGameIdAndNpcOrderByCreatedAtDesc(game.getId(), true).get(0).getId();

        MvcResult roomRes = mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms")
                        .session(login("rpg_master_19")).with(csrf())
                        .param("title", "Ворота"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        Long roomId = Long.parseLong(roomRes.getResponse().getRedirectedUrl().replaceAll(".*rooms/", ""));

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms/" + roomId + "/posts")
                        .session(login("rpg_master_19")).with(csrf())
                        .param("content", "Ворота заперты."))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms/" + roomId + "/posts")
                        .session(login("rpg_master_19")).with(csrf())
                        .param("content", "Кто идёт?")
                        .param("sheetId", String.valueOf(npcId)))
                .andExpect(status().is3xxRedirection());

        User player = createUser("rpg_player_19", UserRole.RESIDENT);
        applyFor("rpg_player_19", game, "Геральт");
        acceptFor(game, player.getId());
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms/" + roomId + "/posts")
                        .session(login("rpg_player_19")).with(csrf())
                        .param("content", "Я Геральт, ищу грош."))
                .andExpect(status().is3xxRedirection());

        MvcResult pageRes = mockMvc.perform(get("/rpg/games/" + game.getId() + "/rooms/" + roomId)
                        .session(login("rpg_master_19")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Мастер")))
                .andExpect(content().string(containsString("Стражник Гора")))
                .andExpect(content().string(containsString("Геральт")))
                .andExpect(content().string(not(containsString("Пост ведущего"))))
                .andReturn();
        String page = pageRes.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        int highlight = 0;
        int idx = 0;
        while ((idx = page.indexOf("card shadow-sm mb-3 post-master", idx)) != -1) {
            highlight++;
            idx += 4;
        }
        assertThat(highlight).isEqualTo(1);
    }

    @Test
    void applyEditShowsClassAndRaceUpTopAsSelectOptions() throws Exception {
        User master = createUser("rpg_master_20", UserRole.RESIDENT);
        Game game = createGameFor(master, "Анкета-редактирование", GameSystem.DND, GameVisibility.OPEN);
        createUser("rpg_player_20", UserRole.RESIDENT);

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/apply").session(login("rpg_player_20")).with(csrf())
                        .param("name", "Эльфийка")
                        .param("kventa", "Странница."))
                .andExpect(status().is3xxRedirection());

        Long playerId = userRepository.findByUsername("rpg_player_20").orElseThrow().getId();
        Long sheetId = applicationRepository.findByGameIdAndUserId(game.getId(), playerId).orElseThrow().getSheetId();
        Map<String, SheetAttribute> attrs = attributeRepository.findBySheetIdOrderByPositionAsc(sheetId).stream()
                .collect(Collectors.toMap(SheetAttribute::getKey, Function.identity()));
        attrs.get("class").setPosition(410);
        attrs.get("race").setPosition(420);
        attributeRepository.saveAll(attrs.values());

        MvcResult pageRes = mockMvc.perform(get("/rpg/games/" + game.getId() + "/apply")
                        .session(login("rpg_player_20")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Лист персонажа и заявка")))
                .andReturn();
        String page = pageRes.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(page.indexOf("Класс")).isGreaterThan(-1);
        assertThat(page.indexOf("Класс")).isLessThan(page.indexOf("Характеристики"));
        assertThat(page.indexOf("Раса")).isLessThan(page.indexOf("Характеристики"));
        assertThat(page).contains(">Бард</option>");
        assertThat(page).contains(">Эльф</option>");
    }

    @Test
    void whitelistEditorShowsOnlyAddedUsers() throws Exception {
        User master = createUser("rpg_master_wl2", UserRole.RESIDENT);
        User invited1 = createUser("rpg_wl_invite_1", UserRole.RESIDENT);
        User invited2 = createUser("rpg_wl_invite_2", UserRole.RESIDENT);
        User outsider = createUser("rpg_wl_outsider", UserRole.RESIDENT);
        Game game = createGame(master, "Закрытая-WL", "Описание.", GameSystem.VERBAL, GameVisibility.CLOSED, GameStatus.RECRUITING);
        MockHttpSession masterSession = login("rpg_master_wl2");
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/whitelist/add").session(masterSession).with(csrf())
                        .param("userId", String.valueOf(invited1.getId())))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/whitelist/add").session(masterSession).with(csrf())
                        .param("userId", String.valueOf(invited2.getId())))
                .andExpect(status().is3xxRedirection());
        assertThat(outsider.getId()).isPositive();

        String page = mockMvc.perform(get("/rpg/games/" + game.getId()).session(masterSession))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(page).contains("data-wl-user=\"rpg_wl_invite_1\"");
        assertThat(page).contains("data-wl-user=\"rpg_wl_invite_2\"");
        assertThat(page).doesNotContain("data-wl-user=\"rpg_wl_outsider\"");
        int removeButtons = 0;
        int pos = 0;
        while ((pos = page.indexOf("Убрать", pos)) != -1) {
            removeButtons++;
            pos += 2;
        }
        assertThat(removeButtons).isEqualTo(2);
    }

    @Test
    void masterConfiguresCustomSheetAttributes() throws Exception {
        User master = createUser("rpg_master_custom2", UserRole.RESIDENT);
        User player = createUser("rpg_player_custom2", UserRole.RESIDENT);
        Game game = createGame(master, "Кастом-правила", "Своя система.", GameSystem.CUSTOM, GameVisibility.OPEN, GameStatus.RECRUITING);
        MockHttpSession masterSession = login("rpg_master_custom2");

        mockMvc.perform(get("/rpg/games/" + game.getId()).session(masterSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Анкета")))
                .andExpect(content().string(containsString("/attributes")));

        mockMvc.perform(get("/rpg/games/" + game.getId() + "/attributes").session(masterSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Список пуст")));

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/attributes").session(masterSession).with(csrf())
                        .param("name", "Слава")
                        .param("attributeType", "NUMERIC"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/attributes").session(masterSession).with(csrf())
                        .param("name", "Тень")
                        .param("attributeType", "TEXT"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/rpg/games/" + game.getId() + "/attributes").session(masterSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Слава")))
                .andExpect(content().string(containsString("Тень")));

        MockHttpSession playerSession = login("rpg_player_custom2");
        mockMvc.perform(get("/rpg/games/" + game.getId() + "/attributes").session(playerSession))
                .andExpect(status().isForbidden());

        MvcResult applyRes = mockMvc.perform(get("/rpg/games/" + game.getId() + "/apply").session(playerSession))
                .andExpect(status().isOk())
                .andReturn();
        String apply = applyRes.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(apply).contains("Параметры");
        assertThat(apply).doesNotContain("Навыки");
        assertThat(apply.indexOf("Слава")).isLessThan(apply.indexOf("Тень"));
    }

    @Test
    void anonymousSeesNonPrivateRoomOfPublicGame() throws Exception {
        User master = createUser("rpg_master_roompub", UserRole.RESIDENT);
        Game game = createGame(master, "Публичная комната анониму", "Для всех.",
                GameSystem.VERBAL, GameVisibility.PUBLIC, GameStatus.RECRUITING);

        MockHttpSession masterSession = null;
        for (var u : userRepository.findAll()) {
            if (u.getId().equals(game.getMasterId())) {
                masterSession = login(u.getUsername());
                break;
            }
        }
        MvcResult rr = mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms")
                        .session(masterSession).with(csrf())
                        .param("title", "Общий зал")
                        .param("privateRoom", "false"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        Long roomId = Long.parseLong(rr.getResponse().getRedirectedUrl().replaceAll(".*rooms/", ""));

        mockMvc.perform(get("/rpg/games/" + game.getId() + "/rooms/" + roomId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Общий зал")))
                .andExpect(content().string(containsString("Вы")));
    }

    @Test
    void unStarRemovesWholeKindOfFavorite() throws Exception {
        User master = createUser("rpg_master_rmkind", UserRole.RESIDENT);
        Game game = createGame(master, "Снятие вида", "Фильтр по комбинации.",
                GameSystem.VERBAL, GameVisibility.PUBLIC, GameStatus.RECRUITING);
        MockHttpSession ms = null;
        for (var u : userRepository.findAll()) {
            if (u.getId().equals(game.getMasterId())) {
                ms = login(u.getUsername());
                break;
            }
        }
        MvcResult rr = mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms").session(ms).with(csrf())
                        .param("title", "Комната вида"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        Long roomId = Long.parseLong(rr.getResponse().getRedirectedUrl().replaceAll(".*rooms/", ""));

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms/" + roomId + "/saved-rolls")
                        .session(ms).with(csrf())
                        .param("die", "d20").param("count", "1").param("modifier", "2").param("rollComment", "Ярость"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms/" + roomId + "/saved-rolls")
                        .session(ms).with(csrf())
                        .param("die", "d20").param("count", "1").param("modifier", "2").param("rollComment", "Ярость"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
        assertThat(savedRollRepository.findByUserIdOrderByCreatedAtDesc(master.getId())).hasSize(1);

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms/" + roomId + "/saved-rolls/remove-kind")
                        .session(ms).with(csrf())
                        .param("die", "d20").param("count", "1").param("modifier", "2").param("rollComment", "Ярость"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
        assertThat(savedRollRepository.findByUserIdOrderByCreatedAtDesc(master.getId())).isEmpty();

        mockMvc.perform(get("/rpg/games/" + game.getId() + "/rooms/" + roomId).session(ms))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Ярость"))));
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

    private Game createGame(User master, String title, String description, GameSystem system,
                            GameVisibility visibility, GameStatus status) {
        Game game = new Game();
        game.setTitle(title);
        game.setDescription(description);
        game.setMasterId(master.getId());
        game.setSystem(system);
        game.setVisibility(visibility);
        game.setStatus(status);
        return gameRepository.save(game);
    }

    private Game createGameFor(User master, String title, GameSystem system, GameVisibility visibility) {
        return createGame(master, title, "Описание test.", system, visibility, GameStatus.RECRUITING);
    }

    private Long applyFor(String playerUsername, Game game, String sheetName) throws Exception {
        User player = userRepository.findByUsername(playerUsername).orElseThrow();
        MockHttpSession session = login(playerUsername);
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/apply").session(session).with(csrf())
                        .param("name", sheetName)
                        .param("kventa", "Квента: короткая история."))
                .andExpect(status().is3xxRedirection());
        return player.getId();
    }

    private void acceptFor(Game game, Long playerId) throws Exception {
        var app = applicationRepository.findByGameIdAndUserId(game.getId(), playerId).orElseThrow();
        MockHttpSession masterSession = null;
        for (var u : userRepository.findAll()) {
            if (u.getId().equals(game.getMasterId())) {
                mockMvc.perform(post("/rpg/games/" + game.getId() + "/applications/" + app.getId() + "/accept")
                                .session(login(u.getUsername())).with(csrf()))
                        .andExpect(status().is3xxRedirection());
                return;
            }
        }
        org.assertj.core.api.Assertions.fail("master session not found");
    }
}