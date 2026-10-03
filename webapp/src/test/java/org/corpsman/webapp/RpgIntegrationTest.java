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
import org.corpsman.rpg.dto.SystemAttributeDto;
import org.corpsman.rpg.model.AttributeType;
import org.corpsman.rpg.model.CustomAttribute;
import org.corpsman.rpg.model.GameSystemDefinition;
import org.corpsman.rpg.model.ListKind;
import org.corpsman.rpg.repository.CustomAttributeRepository;
import org.corpsman.rpg.repository.DiceRollRepository;
import org.corpsman.rpg.repository.GameSystemDefinitionRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
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

import java.util.ArrayList;
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
    private JdbcTemplate jdbcTemplate;

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
    private GameSystemDefinitionRepository definitionRepository;

    @Autowired
    private CustomAttributeRepository customAttributeRepository;

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
        User resident = createUser("auto_rpg_master_1", UserRole.RESIDENT);
        MockHttpSession session = login("auto_rpg_master_1");

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
        User master = createUser("auto_rpg_master_2", UserRole.RESIDENT);
        MockHttpSession session = login("auto_rpg_master_2");
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
        createUser("auto_rpg_guest_1", UserRole.GUEST);
        MockHttpSession session = login("auto_rpg_guest_1");

        mockMvc.perform(post("/rpg/games").session(session).with(csrf())
                        .param("title", "Публичная игра")
                        .param("description", "Описание.")
                        .param("visibility", "PUBLIC"))
                .andExpect(status().is3xxRedirection());

        Game game = gameRepository.findByMasterIdOrderByCreatedAtDesc(
                userRepository.findByUsername("auto_rpg_guest_1").orElseThrow().getId()).get(0);
        assertThat(game.getVisibility()).isEqualTo(GameVisibility.GUEST);
    }

    @Test
    void guestSeesPublicButNotOpenGames() throws Exception {
        User host = createUser("auto_rpg_host_2", UserRole.RESIDENT);
        Game publicGame = createGame(host, "Общая", "Для всех.", GameSystem.VERBAL, GameVisibility.PUBLIC, GameStatus.RECRUITING);
        Game openGame = createGame(host, "Своя", "Для резидентов.", GameSystem.VERBAL, GameVisibility.OPEN, GameStatus.RECRUITING);
        createUser("auto_rpg_guest_2", UserRole.GUEST);
        MockHttpSession session = login("auto_rpg_guest_2");

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
        User host = createUser("auto_rpg_host_anon", UserRole.RESIDENT);
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
        createUser("auto_rpg_guest_3", UserRole.GUEST);
        MockHttpSession session = login("auto_rpg_guest_3");

        mockMvc.perform(post("/rpg/games").session(session).with(csrf())
                        .param("title", "Игра гостя")
                        .param("description", "Гостевой стол.")
                        .param("visibility", "GUEST")
                        .param("status", "RECRUITING"))
                .andExpect(status().is3xxRedirection());

        Game game = gameRepository.findByMasterIdOrderByCreatedAtDesc(
                userRepository.findByUsername("auto_rpg_guest_3").orElseThrow().getId()).get(0);

        mockMvc.perform(get("/rpg/games/" + game.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Игра гостя")))
                .andExpect(content().string(containsString("Редактировать")));
    }

    @Test
    void closedGameHiddenUntilWhitelisted() throws Exception {
        User master = createUser("auto_rpg_master_3", UserRole.RESIDENT);
        User resident = createUser("auto_rpg_res_1", UserRole.RESIDENT);
        Game game = createGame(master, "Тайный клуб", "Только по приглашению.", GameSystem.VERBAL, GameVisibility.CLOSED, GameStatus.RECRUITING);
        MockHttpSession resSession = login("auto_rpg_res_1");

        mockMvc.perform(get("/rpg/games").session(resSession))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Тайный клуб"))));
        mockMvc.perform(get("/rpg/games/" + game.getId()).session(resSession))
                .andExpect(status().isForbidden());

        MockHttpSession masterSession = login("auto_rpg_master_3");
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
        User master = createUser("auto_rpg_master_4", UserRole.RESIDENT);
        User player = createUser("auto_rpg_player_4", UserRole.RESIDENT);
        Game game = createGame(master, "Набор", "Ищем двоих.", GameSystem.VERBAL, GameVisibility.OPEN, GameStatus.RECRUITING);
        MockHttpSession session = login("auto_rpg_player_4");

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

        MockHttpSession masterSession = login("auto_rpg_master_4");
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
        User master = createUser("auto_rpg_master_dndui", UserRole.RESIDENT);
        User player = createUser("auto_rpg_player_dndui", UserRole.RESIDENT);
        Game game = createGame(master, "DND-UI-тест", "Проверка формы листа.", GameSystem.DND, GameVisibility.OPEN, GameStatus.RECRUITING);

        MockHttpSession session = login("auto_rpg_player_dndui");
        MvcResult pageRes = mockMvc.perform(get("/rpg/games/" + game.getId() + "/apply").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Бонус мастерства")))
                .andExpect(content().string(containsString("prof-circle")))
                .andExpect(content().string(containsString("data-flag-max=\"2\"")))
                .andExpect(content().string(containsString("data-flag-max=\"1\"")))
                .andExpect(content().string(containsString("data-num-view=\"level\">1</span>")))
                .andExpect(content().string(containsString("data-num-view=\"ability:strength\">10</span>")))
                .andExpect(content().string(containsString("data-derived-of=\"save:strength\"")))
                .andExpect(content().string(containsString("data-simple-add=\"inventory\"")))
                .andExpect(content().string(containsString("id=\"spell-slots-block\"")))
                .andExpect(content().string(containsString("data-role=\"current\"")))
                .andExpect(content().string(containsString("data-role=\"max\"")))
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
        // Ячейки заклинаний — отдельный блок, в общем списке параметров их нет.
        assertThat(page).doesNotContain("Ячейки 1 ур.");
        // Все девять уровней видны в форме, даже если ячеек нет.
        // 9 уровней × 2 скрытых поля (текущее и максимальное) + 9 строк.
        assertThat(page.split("class=\"spell-slot-value\"", -1)).hasSize(19);
        for (int level = 1; level <= 9; level++) {
            assertThat(page).contains("Ур. " + level);
            assertThat(page).contains("data-slot-row=\"" + level + "\"");
        }

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
    void dndSheetShowsEmptyTextFieldsAsDash() throws Exception {
        User master = createUser("auto_rpg_master_dash", UserRole.RESIDENT);
        createUser("auto_rpg_player_dash", UserRole.RESIDENT);
        Game game = createGame(master, "Прочерки", "Пустые текстовые поля.", GameSystem.DND, GameVisibility.OPEN, GameStatus.RECRUITING);
        Long playerId = applyFor("auto_rpg_player_dash", game, "Пустой лист");
        acceptFor(game, playerId);
        Long sheetId = sheetIdFor(game, playerId);

        // Класс, раса и предыстория не заданы — все три показываются с прочерком.
        String page = sheetPage(game, sheetId, "auto_rpg_player_dash");

        assertThat(page).contains("Класс</span>");
        assertThat(page).contains("Раса</span>");
        assertThat(page).contains("Предыстория</span>");
        assertThat(page).contains("<div class=\"rpg-value\">—</div>");
    }

    @Test
    void dndSheetShowsSpellSlotsAsSingleBlockWithCurrentSlashMax() throws Exception {
        User master = createUser("auto_rpg_master_slots", UserRole.RESIDENT);
        createUser("auto_rpg_player_slots", UserRole.RESIDENT);
        Game game = createGame(master, "Ячейки", "Блок ячеек.", GameSystem.DND, GameVisibility.OPEN, GameStatus.RECRUITING);
        Long playerId = applyFor("auto_rpg_player_slots", game, "Маг");
        acceptFor(game, playerId);
        Long sheetId = sheetIdFor(game, playerId);
        setNumeric(sheetId, "spell_slot_max:1", 4);
        setNumeric(sheetId, "spell_slot_current:1", 2);
        setNumeric(sheetId, "spell_slot_max:3", 1);

        String page = sheetPage(game, sheetId, "auto_rpg_player_slots");

        assertThat(page).contains("Ячейки</span>");
        // Никаких отдельных разделов «Ячейки N ур.» в общем списке параметров.
        assertThat(page).doesNotContain("Ячейки 1 ур.");
        assertThat(page).doesNotContain("Ячейки 3 ур.");
        // Нулевые уровни скрыты, непустые показаны.
        assertThat(page).contains("Ур. 1");
        assertThat(page).contains("Ур. 3");
        assertThat(page).doesNotContain("Ур. 2");
        assertThat(page).doesNotContain("Ур. 5");
        // Счётчик «текущее / максимальное» одной строкой.
        assertThat(page).contains("<span class=\"text-muted\">/</span>");
        // Блок ровно один, в нём только непустые уровни (1 и 3).
        assertThat(page.split("bi-lightning-charge", -1)).hasSize(2);
        assertThat(page.split("class=\"spell-slot-level\"", -1)).hasSize(3);
    }

    @Test
    void sheetViewKeepsListsReadOnlyExceptInventory() throws Exception {
        User master = createUser("auto_rpg_master_ro", UserRole.RESIDENT);
        createUser("auto_rpg_player_ro", UserRole.RESIDENT);
        Game game = createGame(master, "Только чтение", "Просмотр без правок.",
                GameSystem.DND, GameVisibility.OPEN, GameStatus.RECRUITING);
        Long playerId = applyFor("auto_rpg_player_ro", game, "Наблюдатель");
        acceptFor(game, playerId);
        Long sheetId = sheetIdFor(game, playerId);
        int spellsIndex = attributeIndex(sheetId, "spells");
        MockHttpSession session = login("auto_rpg_player_ro");

        // Наполняем сложный список через форму редактирования.
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/characters/" + sheetId + "/edit")
                        .session(session).with(csrf())
                        .param("name", "Наблюдатель")
                        .param("attributes[" + spellsIndex + "].key", "spells")
                        .param("attributes[" + spellsIndex + "].type", "LIST")
                        .param("attributes[" + spellsIndex + "].listKind", "COMPLEX")
                        .param("attributes[" + spellsIndex + "].listJson",
                                "[{\"header\":\"Огненный шар\",\"description\":\"Урон 8d6\",\"level\":3}]"))
                .andExpect(status().is3xxRedirection());

        String page = sheetPage(game, sheetId, "auto_rpg_player_ro");

        // Сложный список виден...
        assertThat(page).contains("Огненный шар").contains("Урон 8д6".replace("д", "d")).contains("Ур. 3");
        // ...но правки списков в просмотре нет.
        assertThat(page).doesNotContain("lists/spells/add");
        assertThat(page).doesNotContain("lists/spells/remove");

        // Инвентарь — простой список, его править из просмотра можно (оговорено отдельно).
        assertThat(page).contains("inventory-add");
        assertThat(page).contains("name=\"itemHeader\"");

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/characters/" + sheetId + "/inventory-add")
                        .session(session).with(csrf())
                        .param("itemHeader", "Факел"))
                .andExpect(status().is3xxRedirection());

        String withItem = sheetPage(game, sheetId, "auto_rpg_player_ro");
        assertThat(withItem).contains("Факел");
        assertThat(withItem).contains("inventory-remove");
        assertThat(withItem).contains("name=\"itemIndex\"");
    }

    @Test
    void inventoryCanBeEditedFromSheetView() throws Exception {
        User master = createUser("auto_rpg_master_inv", UserRole.RESIDENT);
        createUser("auto_rpg_player_inv", UserRole.RESIDENT);
        Game game = createGame(master, "Инвентарь", "Правка из просмотра.", GameSystem.DND, GameVisibility.OPEN, GameStatus.RECRUITING);
        Long playerId = applyFor("auto_rpg_player_inv", game, "Кладоискатель");
        acceptFor(game, playerId);
        Long sheetId = sheetIdFor(game, playerId);
        MockHttpSession session = login("auto_rpg_player_inv");

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/characters/" + sheetId + "/inventory-add")
                        .session(session).with(csrf())
                        .param("itemHeader", "Факел"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/rpg/games/" + game.getId() + "/characters/" + sheetId));
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/characters/" + sheetId + "/inventory-add")
                        .session(session).with(csrf())
                        .param("itemHeader", "Хлеб"))
                .andExpect(status().is3xxRedirection());

        String page = sheetPage(game, sheetId, "auto_rpg_player_inv");
        assertThat(page).contains("Факел").contains("Хлеб");

        // Инвентарь хранится простым списком — JSON-массивом строк.
        String stored = listValues(sheetId, "inventory");
        assertThat(stored).contains("\"Факел\"").doesNotContain("header");

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/characters/" + sheetId + "/inventory-remove")
                        .session(session).with(csrf())
                        .param("itemIndex", "0"))
                .andExpect(status().is3xxRedirection());

        assertThat(sheetPage(game, sheetId, "auto_rpg_player_inv")).doesNotContain("Факел").contains("Хлеб");
    }

    @Test
    void simpleListIsRenderedAsPlainItemsAndComplexAsCards() throws Exception {
        User master = createUser("auto_rpg_master_kinds", UserRole.RESIDENT);
        createUser("auto_rpg_player_kinds", UserRole.RESIDENT);
        Game game = createGame(master, "Виды списков", "Простой и сложный.", GameSystem.DND, GameVisibility.OPEN, GameStatus.RECRUITING);
        Long playerId = applyFor("auto_rpg_player_kinds", game, "Смешанный");
        acceptFor(game, playerId);
        Long sheetId = sheetIdFor(game, playerId);
        MockHttpSession session = login("auto_rpg_player_kinds");
        int invIdx = attributeIndex(sheetId, "inventory");
        int spellsIdx = attributeIndex(sheetId, "spells");
        int featsIdx = attributeIndex(sheetId, "feats");

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/characters/" + sheetId + "/edit")
                        .session(session).with(csrf())
                        .param("name", "Смешанный")
                        .param("attributes[" + invIdx + "].key", "inventory")
                        .param("attributes[" + invIdx + "].type", "LIST")
                        .param("attributes[" + invIdx + "].listKind", "SIMPLE")
                        .param("attributes[" + invIdx + "].listText", "Факел\nХлеб")
                        .param("attributes[" + spellsIdx + "].key", "spells")
                        .param("attributes[" + spellsIdx + "].type", "LIST")
                        .param("attributes[" + spellsIdx + "].listKind", "COMPLEX")
                        .param("attributes[" + spellsIdx + "].listJson",
                                "[{\"header\":\"Огненный шар\",\"description\":\"Урон\",\"level\":3}]")
                        .param("attributes[" + featsIdx + "].key", "feats")
                        .param("attributes[" + featsIdx + "].type", "LIST")
                        .param("attributes[" + featsIdx + "].listKind", "COMPLEX")
                        .param("attributes[" + featsIdx + "].listJson",
                                "[{\"header\":\"Второе дыхание\",\"description\":\"Раз в день\",\"level\":1}]"))
                .andExpect(status().is3xxRedirection());

        assertThat(listKindOf(sheetId, "inventory")).isEqualTo("SIMPLE");
        assertThat(listKindOf(sheetId, "spells")).isEqualTo("COMPLEX");
        assertThat(listKindOf(sheetId, "feats")).isEqualTo("COMPLEX");
        // Простой список — строки без заголовков/описаний.
        assertThat(listValues(sheetId, "inventory")).isEqualTo("[\"Факел\",\"Хлеб\"]");
        // Сложный список — объекты.
        assertThat(listValues(sheetId, "spells")).contains("header").contains("level");

        String page = sheetPage(game, sheetId, "auto_rpg_player_kinds");
        assertThat(page).contains("Факел").contains("Хлеб");
        assertThat(page).contains("Огненный шар").contains("Второе дыхание");
        // У простого списка нет карточек, у сложных — есть.
        assertThat(page).contains("list-item-card");
    }

    @Test
    void dndListsAreComplexAndInventoryIsSimpleInEditForm() throws Exception {
        User master = createUser("auto_rpg_master_kinds2", UserRole.RESIDENT);
        createUser("auto_rpg_player_kinds2", UserRole.RESIDENT);
        Game game = createGame(master, "Виды списков 2", "Проверка формы.", GameSystem.DND, GameVisibility.OPEN, GameStatus.RECRUITING);
        Long playerId = applyFor("auto_rpg_player_kinds2", game, "Проверка");
        acceptFor(game, playerId);
        Long sheetId = sheetIdFor(game, playerId);

        String page = mockMvc.perform(get("/rpg/games/" + game.getId() + "/characters/" + sheetId + "/edit")
                        .session(login("auto_rpg_player_kinds2")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        // Сложные списки D&D: карточки + модалка добавления.
        assertThat(page).contains("list-add-modal-spells");
        assertThat(page).contains("list-add-modal-feats");
        assertThat(page).contains("list-add-modal-skills");
        // Простой инвентарь: карточки + поле ввода + кнопка «+», без модалки.
        assertThat(page).contains("data-simple-input=\"inventory\"");
        assertThat(page).contains("data-simple-cards=\"inventory\"");
        assertThat(page).doesNotContain("list-add-modal-inventory");
        // У ячеек только кнопки, ручного ввода нет; есть предупреждение.
        assertThat(page).contains("btn-slot-step");
        assertThat(page).contains("data-slot-step=\"1\"");
        assertThat(page).contains("data-slot-step=\"-1\"");
        assertThat(page).contains("data-slot-warning");
        assertThat(page).contains("btn-slot-max");
        assertThat(page).contains("data-max-step=\"1\"");
        assertThat(page).contains("data-max-step=\"-1\"");
        assertThat(page).contains("slot-arrows");
        assertThat(page).contains("slot-num");
        // Ручного ввода числа ячеек нет: поля скрытые, значение меняют кнопки.
        assertThat(page).doesNotContain("spell-slot-input");
        assertThat(page).doesNotContain("spell-slots-fill");
        assertThat(page).doesNotContain("syncSpellSlot");
        assertThat(page).contains("list-modal-confirm");
        // Сложные списки без поля ввода: только кнопка «Добавить».
        assertThat(page).doesNotContain("data-list-input");
        assertThat(page).contains("Восстановить");
        assertThat(page).doesNotContain("Заполнить");
        // Подсказки-пересказы убраны.
        assertThat(page).doesNotContain("Текущее не может превышать максимальное");
    }

    @Test
    void librarySystemFormLetsAuthorDeclareParametersAndListKind() throws Exception {
        User master = createUser("auto_rpg_master_lib2", UserRole.RESIDENT);
        MockHttpSession session = login("auto_rpg_master_lib2");

        // Пустая анкета: есть раздел и кнопка добавления.
        String emptyForm = mockMvc.perform(get("/rpg/systems/new").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(emptyForm).contains("Публикация новой системы");
        assertThat(emptyForm).contains("Публичная система");
        assertThat(emptyForm).contains("Состав параметров листа");
        assertThat(emptyForm).contains("id=\"add-attr-btn\"");
        assertThat(emptyForm).contains("data-new-list-kind");
        assertThat(emptyForm).contains("value=\"COMPLEX\"");
        assertThat(emptyForm).contains("value=\"SIMPLE\"");

        // Заполненная анкета: параметры выводятся, вид списка выбирается.
        Long systemId = createLibrarySystemWithTemplate(master, "Система с анкетой");
        String editForm = mockMvc.perform(get("/rpg/systems/" + systemId + "/edit").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(editForm).contains("Мана");
        assertThat(editForm).contains("Заклинания");
        assertThat(editForm).contains("templateAttributes[0].name");
        assertThat(editForm).contains("templateAttributes[1].listKind");
        assertThat(editForm).contains("templateAttributes[2].listKind");

        // Страница системы: кнопка предпросмотра, без ложной подсказки.
        String view = mockMvc.perform(get("/rpg/systems/" + systemId).session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(view).contains("Открыть режим создания");

        // Пробный лист показывает состав параметров системы.
        String preview = mockMvc.perform(get("/rpg/systems/" + systemId + "/character").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(preview).contains("Пробный персонаж");
        assertThat(preview).contains("не сохраняется");
        assertThat(preview).doesNotContain("Подать заявку");
        assertThat(preview).doesNotContain("настраивает ведущий");
        assertThat(preview).contains("Мана");
        assertThat(preview).contains("Заклинания");
        // Сложный список в предпросмотре даёт модалку, простой — поле ввода.
        assertThat(preview).contains("data-list-cards");
        assertThat(preview).contains("data-simple-input");
    }

    @Test
    void librarySystemTemplateIsAppliedToGameWithoutManualSetup() throws Exception {
        User master = createUser("auto_rpg_master_tpl", UserRole.RESIDENT);
        MockHttpSession session = login("auto_rpg_master_tpl");
        Long sysId = createLibrarySystemWithTemplate(master, "Система с анкетой");

        mockMvc.perform(post("/rpg/games").session(session).with(csrf())
                        .param("title", "Игра с готовой анкетой")
                        .param("description", "Параметры приходят из системы.")
                        .param("system", "CUSTOM")
                        .param("customSystemId", String.valueOf(sysId))
                        .param("visibility", "OPEN")
                        .param("status", "RECRUITING"))
                .andExpect(status().is3xxRedirection());

        Long gameId = gameIdByTitle("Игра с готовой анкетой");
        assertThat(gameId).isNotNull();

        // Ведущему не нужно заполнять параметры вручную — они уже созданы.
        List<CustomAttribute> attrs = customAttributeRepository.findByGameIdOrderByPositionAsc(gameId);
        assertThat(attrs).extracting(CustomAttribute::getName)
                .containsExactly("Мана", "Заклинания", "Инвентарь");
        assertThat(attrs).extracting(CustomAttribute::getType)
                .containsExactly(AttributeType.NUMERIC, AttributeType.LIST, AttributeType.LIST);
        assertThat(attrs.get(1).getListKind()).isEqualTo(ListKind.COMPLEX);
        assertThat(attrs.get(2).getListKind()).isEqualTo(ListKind.SIMPLE);
    }

    @Test
    void masterCanChooseSimpleOrComplexListForCustomAttribute() throws Exception {
        User master = createUser("auto_rpg_master_kinds3", UserRole.RESIDENT);
        Game game = createGameFor(master, "Виды списков ведущего", GameSystem.CUSTOM, GameVisibility.OPEN);
        MockHttpSession session = login("auto_rpg_master_kinds3");

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/attributes").session(session).with(csrf())
                        .param("name", "Черты")
                        .param("attributeType", "LIST")
                        .param("listKind", "COMPLEX"))
                .andExpect(status().is3xxRedirection());

        CustomAttribute attr = customAttributeRepository.findByGameIdOrderByPositionAsc(game.getId()).get(0);
        assertThat(attr.getListKind()).isEqualTo(ListKind.COMPLEX);

        String page = mockMvc.perform(get("/rpg/games/" + game.getId() + "/attributes").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(page).contains("name=\"listKind\"");
        assertThat(page).contains("Сложный");
    }

    @Test
    void editSheetLinkOnGamePagePointsToExistingSheet() throws Exception {
        User master = createUser("auto_rpg_master_link", UserRole.RESIDENT);
        createUser("auto_rpg_player_link", UserRole.RESIDENT);
        Game game = createGame(master, "Ссылка на лист", "Проверка ссылки.", GameSystem.DND, GameVisibility.OPEN, GameStatus.RECRUITING);
        Long playerId = applyFor("auto_rpg_player_link", game, "Герой");
        acceptFor(game, playerId);
        Long sheetId = sheetIdFor(game, playerId);

        String page = mockMvc.perform(get("/rpg/games/" + game.getId()).session(login("auto_rpg_player_link")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(page).contains("/rpg/games/" + game.getId() + "/characters/" + sheetId + "/edit");
    }

    @Test
    void systemsButtonHiddenFromAnonymous() throws Exception {
        User master = createUser("auto_rpg_master_anon", UserRole.RESIDENT);
        Game game = createGame(master, "Открытая игра", "Видна анонимным.", GameSystem.DND, GameVisibility.PUBLIC, GameStatus.RECRUITING);

        String anonymous = mockMvc.perform(get("/rpg/games"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(anonymous).doesNotContain("/rpg/systems\"");

        String authorized = mockMvc.perform(get("/rpg/games").session(login("auto_rpg_master_anon")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(authorized).contains("/rpg/systems");
    }

    @Test
    void dndListsAreComplexInViewAndForNewSheet() throws Exception {
        User master = createUser("auto_rpg_master_cplx", UserRole.RESIDENT);
        createUser("auto_rpg_player_cplx", UserRole.RESIDENT);
        Game game = createGame(master, "Сложные списки", "Вид списков D&D.", GameSystem.DND, GameVisibility.OPEN, GameStatus.RECRUITING);
        Long playerId = applyFor("auto_rpg_player_cplx", game, "Мастер");
        acceptFor(game, playerId);
        Long sheetId = sheetIdFor(game, playerId);

        // Форма нового листа: умения, черты, заклинания — сложные, инвентарь простой.
        String applyPage = mockMvc.perform(get("/rpg/games/" + game.getId() + "/apply")
                        .session(login("auto_rpg_player_cplx")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        for (String key : new String[]{"skills", "feats", "spells"}) {
            assertThat(applyPage).contains("data-list-add=\"" + key + "\"");
            assertThat(applyPage).doesNotContain("data-simple-add=\"" + key + "\"");
        }
        assertThat(applyPage).contains("data-simple-add=\"inventory\"");

        // Просмотр листа: те же списки идут блоком сложных.
        String viewPage = sheetPage(game, sheetId, "auto_rpg_player_cplx");
        for (String key : new String[]{"skills", "feats", "spells"}) {
            assertThat(listKindOf(sheetId, key)).isEqualTo("COMPLEX");
        }
        assertThat(listKindOf(sheetId, "inventory")).isEqualTo("SIMPLE");

        // Форма редактирования существующего листа.
        String editPage = mockMvc.perform(get("/rpg/games/" + game.getId() + "/characters/" + sheetId + "/edit")
                        .session(login("auto_rpg_player_cplx")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        for (String key : new String[]{"skills", "feats", "spells"}) {
            assertThat(editPage).contains("data-list-add=\"" + key + "\"");
            assertThat(editPage).doesNotContain("data-simple-add=\"" + key + "\"");
        }
    }

    @Test
    void dndListsRenderAsComplexEvenWithStaleSimpleStamp() throws Exception {
        User master = createUser("auto_rpg_master_stale", UserRole.RESIDENT);
        createUser("auto_rpg_player_stale", UserRole.RESIDENT);
        Game game = createGame(master, "Устаревший вид", "Имитация данных до миграции.",
                GameSystem.DND, GameVisibility.OPEN, GameStatus.RECRUITING);
        Long playerId = applyFor("auto_rpg_player_stale", game, "Пух");
        acceptFor(game, playerId);
        Long sheetId = sheetIdFor(game, playerId);

        // Именно то, что осталось после миграции 3.1.0-02: вид SIMPLE у сложных
        // списков D&D и пустой JSON.
        setListKind(sheetId, "spells", "SIMPLE");
        setListKind(sheetId, "feats", "SIMPLE");
        setListKind(sheetId, "skills", "SIMPLE");
        setListValues(sheetId, "spells", "[]");
        setListValues(sheetId, "feats", "[]");
        setListValues(sheetId, "skills", "[]");

        String editPage = mockMvc.perform(get("/rpg/games/" + game.getId() + "/characters/" + sheetId + "/edit")
                        .session(login("auto_rpg_player_stale")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        for (String key : new String[]{"spells", "feats", "skills"}) {
            // Сложный список: кнопка «Добавить» и всплывающее окно, без поля ввода.
            assertThat(editPage).as("%s: кнопка «Добавить»", key).contains("data-list-add=\"" + key + "\"");
            assertThat(editPage).as("%s: всплывающее окно", key).contains("list-add-modal-" + key);
            assertThat(editPage).as("%s: нет поля ввода", key).doesNotContain("data-simple-input=\"" + key + "\"");
            assertThat(editPage).as("%s: нет кнопки +", key).doesNotContain("data-simple-add=\"" + key + "\"");
        }
        // Инвентарь — простой список: есть поле ввода и кнопка «+».
        assertThat(editPage).contains("data-simple-input=\"inventory\"");
        assertThat(editPage).contains("data-simple-add=\"inventory\"");
        assertThat(editPage).doesNotContain("list-add-modal-inventory");

        // И в просмотре листа списки идут блоком сложных, а не строк простого списка.
        String viewPage = sheetPage(game, sheetId, "auto_rpg_player_stale");
        int complexBadges = count(viewPage, "list-add-modal-");
        assertThat(complexBadges).as("модалок сложных списков в просмотре нет").isZero();
    }

    private int count(String text, String needle) {
        int n = 0;
        int i = text.indexOf(needle);
        while (i >= 0) {
            n++;
            i = text.indexOf(needle, i + needle.length());
        }
        return n;
    }

    @Test
    void systemPageShowsCompositionAndAuthorActions() throws Exception {
        User master = createUser("auto_rpg_master_comp", UserRole.RESIDENT);
        MockHttpSession session = login("auto_rpg_master_comp");
        Long systemId = createLibrarySystemWithTemplate(master, "Система с составом");

        String page = mockMvc.perform(get("/rpg/systems/" + systemId).session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(page).contains("Состав параметров листа");
        assertThat(page).contains("Мана");
        assertThat(page).contains("Заклинания");
        assertThat(page).contains("Инвентарь");
        assertThat(page).contains("Сложный");
        assertThat(page).contains("Простой");
        // Кнопка создания игры ведёт с предвыбранной системой.
        assertThat(page).contains("customSystemId=" + systemId);
        // Форк открывает форму создания с предзаполненными параметрами.
        assertThat(page).contains("/rpg/systems/new?forkFrom=" + systemId);
        assertThat(page).doesNotContain("action=\"/rpg/systems/" + systemId + "/fork\"");
        // Избыточная подсказка убрана.
        assertThat(page).doesNotContain("ведущему не нужно заполнять");
    }

    @Test
    void systemFormHintIsRemovedAndNewRowsGetNames() throws Exception {
        User master = createUser("auto_rpg_master_hint", UserRole.RESIDENT);
        MockHttpSession session = login("auto_rpg_master_hint");

        String form = mockMvc.perform(get("/rpg/systems/new").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(form).doesNotContain("Этот набор переносится в лист персонажа");
        assertThat(form).doesNotContain("ведущему не нужно заполнять");
        // Скрипт назначает name полям шаблона, иначе добавленные параметры не сохранятся.
        assertThat(form).contains("data-new-name");
        assertThat(form).contains("input.name = PREFIX + index + SUFFIX + field");
    }

    @Test
    void createGameFormPreselectsSystemFromQuery() throws Exception {
        User master = createUser("auto_rpg_master_pre", UserRole.RESIDENT);
        MockHttpSession session = login("auto_rpg_master_pre");
        Long systemId = createLibrarySystem(master, "Система для предвыбора");

        String form = mockMvc.perform(get("/rpg/games/new?system=CUSTOM&customSystemId=" + systemId)
                        .session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(form).contains("name=\"system\"");
        assertThat(form).contains("value=\"CUSTOM\" selected=\"selected\"");
        assertThat(form).contains("value=\"" + systemId + "\" selected=\"selected\"");
    }

    @Test
    void complexListAddButtonOpensModal() throws Exception {
        User master = createUser("auto_rpg_master_modal", UserRole.RESIDENT);
        createUser("auto_rpg_player_modal", UserRole.RESIDENT);
        Game game = createGame(master, "Модалка списка", "Кнопка «Добавить».", GameSystem.DND, GameVisibility.OPEN, GameStatus.RECRUITING);
        Long playerId = applyFor("auto_rpg_player_modal", game, "Маг");
        acceptFor(game, playerId);
        Long sheetId = sheetIdFor(game, playerId);

        String page = mockMvc.perform(get("/rpg/games/" + game.getId() + "/characters/" + sheetId + "/edit")
                        .session(login("auto_rpg_player_modal")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        // Без data-bs-toggle/data-bs-target модалка не открывается — кнопка мёртвая.
        for (String key : new String[]{"spells", "feats", "skills"}) {
            assertThat(page).as("%s: кнопка открывает модалку", key)
                    .contains("data-list-add=\"" + key + "\" data-bs-target=\"#list-add-modal-" + key + "\"");
        }
        assertThat(page).contains("data-bs-toggle=\"modal\"");
        assertThat(page).contains("show.bs.modal");
    }

    @Test
    void systemListCardKeepsBadgeStyleAndButtonsInsideCard() throws Exception {
        User master = createUser("auto_rpg_master_card", UserRole.RESIDENT);
        MockHttpSession session = login("auto_rpg_master_card");
        Long systemId = createLibrarySystem(master, "Система для карточки");

        String page = mockMvc.perform(get("/rpg/systems").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        // th:class заменял атрибут целиком и терял .badge (скругления, жирный шрифт).
        assertThat(page).contains("class=\"badge text-bg-success\"");
        assertThat(page).contains("Публичная");
        // th:classappend сохраняет базовые классы.
        assertThat(page).doesNotContain("<span class=\"text-bg-success\">");
        // Кнопки действий — POST-формы внутри футера, перенос строки разрешён.
        assertThat(page).contains("card-footer bg-transparent border-top-0 d-flex flex-wrap");
        // Автору доступны правка и удаление, форк ведёт на форму создания.
        assertThat(page).contains("href=\"/rpg/systems/" + systemId + "/edit\"");
        assertThat(page).contains("href=\"/rpg/systems/new?forkFrom=" + systemId + "\"");
        assertThat(page).contains("href=\"/rpg/systems/" + systemId + "\"");
        // Удаление остаётся POST-формой: GET-ссылки на действие нет.
        assertThat(page).contains("action=\"/rpg/systems/" + systemId + "/delete\"");
        assertThat(page).doesNotContain("href=\"/rpg/systems/" + systemId + "/delete\"");
        // Копия создаётся только после отправки формы, отдельного POST-форка нет.
        assertThat(page).doesNotContain("action=\"/rpg/systems/" + systemId + "/fork\"");
        // «К играм» ведёт на список игр, а не на форму создания.
        assertThat(page).contains("href=\"/rpg/games\"");
    }

    @Test
    void forkOpensCreateFormPrefilledFromSourceSystem() throws Exception {
        User author = createUser("auto_rpg_master_fork_src", UserRole.RESIDENT);
        createUser("auto_rpg_player_forker", UserRole.RESIDENT);
        MockHttpSession session = login("auto_rpg_player_forker");
        Long sourceId = createLibrarySystemWithTemplate(author, "Своя D&D 5e");

        String form = mockMvc.perform(get("/rpg/systems/new").param("forkFrom", String.valueOf(sourceId))
                        .session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        // Название предзаполнено и не совпадает с исходным.
        assertThat(form).contains("value=\"Своя D&amp;D 5e (форк)\"");
        assertThat(form).contains("Система с составом параметров.");
        // Состав параметров исходной системы уже в форме, с типами и видом списка.
        assertThat(form).contains("value=\"Мана\"");
        assertThat(form).contains("value=\"Заклинания\"");
        assertThat(form).contains("value=\"Инвентарь\"");
        assertThat(form).containsPattern("<option value=\"COMPLEX\" selected=\"selected\">Сложный</option>");
        assertThat(form).contains("action=\"/rpg/systems/new\"");
        assertThat(form).contains("data-fork-banner");
        // Форк приватный по умолчанию: галка публичности снята, скрытое поле false на месте.
        assertThat(form).doesNotContain("id=\"published\" checked");
        assertThat(form).contains("<input type=\"hidden\" name=\"published\" value=\"false\"/>");

        // Сохранение формы создаёт отдельную систему с копией состава.
        mockMvc.perform(post("/rpg/systems/new").session(session).with(csrf())
                        .param("name", "Своя D&D 5e (форк)")
                        .param("description", "Система с составом параметров.")
                        .param("published", "false")
                        .param("templateAttributes[0].name", "Мана")
                        .param("templateAttributes[0].type", "NUMERIC")
                        .param("templateAttributes[1].name", "Заклинания")
                        .param("templateAttributes[1].type", "LIST")
                        .param("templateAttributes[1].listKind", "COMPLEX")
                        .param("templateAttributes[2].name", "Инвентарь")
                        .param("templateAttributes[2].type", "LIST")
                        .param("templateAttributes[2].listKind", "SIMPLE"))
                .andExpect(status().is3xxRedirection());

        GameSystemDefinition fork = definitionRepository.findAll().stream()
                .filter(sys -> "Своя D&D 5e (форк)".equals(sys.getName()))
                .findFirst().orElseThrow();
        assertThat(fork.getId()).isNotEqualTo(sourceId);
        assertThat(fork.getAuthorId()).isNotEqualTo(author.getId());
        assertThat(fork.isPublic()).isFalse();
        assertThat(fork.getTemplateAttributeList()).hasSize(3);
        assertThat(fork.getTemplateAttributeList().get(1).getName()).isEqualTo("Заклинания");
        assertThat(fork.getTemplateAttributeList().get(1).listKindOrDefault()).isEqualTo(ListKind.COMPLEX);
        // Исходная система не тронута.
        assertThat(definitionRepository.findById(sourceId).orElseThrow().getTemplateAttributeList()).hasSize(3);
    }

    @Test
    void uncheckedVisibilityCreatesPrivateSystem() throws Exception {
        createUser("auto_rpg_master_privdef", UserRole.RESIDENT);
        MockHttpSession session = login("auto_rpg_master_privdef");

        // Галочка «Публичная система» снята: браузер присылает published=false
        // из скрытого поля. Без него значение по умолчанию сделало бы систему публичной.
        mockMvc.perform(post("/rpg/systems/new").session(session).with(csrf())
                        .param("name", "Приватная по умолчанию")
                        .param("published", "false"))
                .andExpect(status().is3xxRedirection());

        GameSystemDefinition created = definitionRepository.findAll().stream()
                .filter(sys -> "Приватная по умолчанию".equals(sys.getName()))
                .findFirst().orElseThrow();
        assertThat(created.isPublic()).isFalse();
    }

    @Test
    void forkNameGetsSuffixWhenCopyAlreadyExists() throws Exception {
        User author = createUser("auto_rpg_master_fork_dup", UserRole.RESIDENT);
        User forker = createUser("auto_rpg_player_forker2", UserRole.RESIDENT);
        MockHttpSession session = login("auto_rpg_player_forker2");
        Long sourceId = createLibrarySystem(author, "Система для повторного форка");
        createLibrarySystem(forker, "Система для повторного форка (форк)");

        String form = mockMvc.perform(get("/rpg/systems/new").param("forkFrom", String.valueOf(sourceId))
                        .session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(form).contains("value=\"Система для повторного форка (форк) 1\"");
    }

    @Test
    void forkOfPrivateSystemOfAnotherAuthorDenied() throws Exception {
        User author = createUser("auto_rpg_master_fork_priv", UserRole.RESIDENT);
        createUser("auto_rpg_player_forker3", UserRole.RESIDENT);
        MockHttpSession session = login("auto_rpg_player_forker3");
        Long privateId = createLibrarySystemWithTemplate(author, "Приватная система");
        GameSystemDefinition privateSystem = definitionRepository.findById(privateId).orElseThrow();
        privateSystem.setPublic(false);
        definitionRepository.save(privateSystem);

        mockMvc.perform(get("/rpg/systems/new").param("forkFrom", String.valueOf(privateId)).session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void systemPreviewHasNoSaveButton() throws Exception {
        User author = createUser("auto_rpg_master_preview", UserRole.RESIDENT);
        Long systemId = createLibrarySystemWithTemplate(author, "Система для предпросмотра");

        String page = mockMvc.perform(get("/rpg/systems/" + systemId + "/character")
                        .session(login("auto_rpg_master_preview")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(page).doesNotContain("Не сохраняется");
        assertThat(page).doesNotContain("disabled");
        assertThat(page).contains("К системе");
        // Форма предпросмотра не отправляется даже по Enter: иначе POST дал бы 405.
        assertThat(page).contains("onsubmit=\"return false\"");
    }

    @Test
    void authorCanEditAndDeleteOwnSystem() throws Exception {
        User author = createUser("auto_rpg_master_owner", UserRole.RESIDENT);
        MockHttpSession session = login("auto_rpg_master_owner");
        Long systemId = createLibrarySystemWithTemplate(author, "Система автора");

        mockMvc.perform(get("/rpg/systems/" + systemId + "/edit").session(session))
                .andExpect(status().isOk());

        mockMvc.perform(post("/rpg/systems/" + systemId + "/edit").session(session).with(csrf())
                        .param("name", "Система автора (обновлённая)")
                        .param("description", "Новое описание.")
                        .param("templateAttributes[0].name", "Мана")
                        .param("templateAttributes[0].type", "NUMERIC"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/rpg/systems/" + systemId));

        GameSystemDefinition updated = definitionRepository.findById(systemId).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Система автора (обновлённая)");
        assertThat(updated.getDescription()).isEqualTo("Новое описание.");
        assertThat(updated.getTemplateAttributeList()).hasSize(1);

        mockMvc.perform(post("/rpg/systems/" + systemId + "/delete").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/rpg/systems"));

        assertThat(definitionRepository.findById(systemId)).isEmpty();
    }

    @Test
    void strangerCannotEditOrDeleteSystem() throws Exception {
        User author = createUser("auto_rpg_master_owner2", UserRole.RESIDENT);
        createUser("auto_rpg_player_stranger", UserRole.RESIDENT);
        MockHttpSession session = login("auto_rpg_player_stranger");
        Long systemId = createLibrarySystemWithTemplate(author, "Чужая система");

        mockMvc.perform(get("/rpg/systems/" + systemId + "/edit").session(session))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/rpg/systems/" + systemId + "/edit").session(session).with(csrf())
                        .param("name", "Захвачено"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/rpg/systems/" + systemId + "/delete").session(session).with(csrf()))
                .andExpect(status().isForbidden());

        GameSystemDefinition untouched = definitionRepository.findById(systemId).orElseThrow();
        assertThat(untouched.getName()).isEqualTo("Чужая система");
        assertThat(untouched.getTemplateAttributeList()).hasSize(3);
    }

    @Test
    void versionBadgeAndColumnAreGone() throws Exception {
        User author = createUser("auto_rpg_master_nover", UserRole.RESIDENT);
        MockHttpSession session = login("auto_rpg_master_nover");
        Long systemId = createLibrarySystemWithTemplate(author, "Система без версии");

        String list = mockMvc.perform(get("/rpg/systems").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String view = mockMvc.perform(get("/rpg/systems/" + systemId).session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        // Бейдж версии удалён, колонка из базы тоже.
        assertThat(list).doesNotContain(">v1<");
        assertThat(list).doesNotContain(">v</");
        assertThat(view).doesNotContain(">v1<");
        assertThat(view).doesNotContain(">v</");
        assertThat(columnExists("rpg_system_definitions", "system_version")).isFalse();
    }

    @Test
    void anyUserCanForkButOnlyAuthorEditsAndDeletes() throws Exception {
        User author = createUser("auto_rpg_master_forkui", UserRole.RESIDENT);
        createUser("auto_rpg_player_forkui", UserRole.RESIDENT);
        MockHttpSession authorSession = login("auto_rpg_master_forkui");
        MockHttpSession otherSession = login("auto_rpg_player_forkui");
        Long systemId = createLibrarySystemWithTemplate(author, "Публичная система автора");

        String listOther = mockMvc.perform(get("/rpg/systems").session(otherSession))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String cardOther = cardWithId(listOther, systemId);

        // Чужому пользователю доступен форк и просмотр, но не правка и не удаление.
        assertThat(cardOther).contains("href=\"/rpg/systems/new?forkFrom=" + systemId + "\"");
        assertThat(cardOther).contains("href=\"/rpg/systems/" + systemId + "\"");
        assertThat(cardOther).doesNotContain("href=\"/rpg/systems/" + systemId + "/edit\"");
        assertThat(cardOther).doesNotContain("/rpg/systems/" + systemId + "/delete");

        String listAuthor = mockMvc.perform(get("/rpg/systems").session(authorSession))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String cardAuthor = cardWithId(listAuthor, systemId);

        // Автору доступны все три действия.
        assertThat(cardAuthor).contains("href=\"/rpg/systems/new?forkFrom=" + systemId + "\"");
        assertThat(cardAuthor).contains("href=\"/rpg/systems/" + systemId + "/edit\"");
        assertThat(cardAuthor).contains("action=\"/rpg/systems/" + systemId + "/delete\"");

        // На странице системы то же самое.
        String viewOther = mockMvc.perform(get("/rpg/systems/" + systemId).session(otherSession))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(viewOther).contains("href=\"/rpg/systems/new?forkFrom=" + systemId + "\"");
        assertThat(viewOther).doesNotContain("/edit");
        assertThat(viewOther).doesNotContain("/delete");
    }

    @Test
    void newSystemFormHasHiddenFieldForVisibilityCheckbox() throws Exception {
        createUser("auto_rpg_master_chk", UserRole.RESIDENT);
        MockHttpSession session = login("auto_rpg_master_chk");

        String form = mockMvc.perform(get("/rpg/systems/new").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(form).contains("<input type=\"hidden\" name=\"published\" value=\"false\"/>");
    }

    @Test
    void gameFormHasHiddenFieldForSheetVisibilityCheckbox() throws Exception {
        User master = createUser("auto_rpg_master_chk2", UserRole.RESIDENT);
        MockHttpSession session = login("auto_rpg_master_chk2");

        String form = mockMvc.perform(get("/rpg/games/new").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        // Снятая галочка не отправляется, а поле по умолчанию true — без скрытого
        // поля «анкета скрыта» не работала бы.
        assertThat(form).contains("<input type=\"hidden\" name=\"attributesVisible\" value=\"false\"/>");
    }

    @Test
    void uncheckedSheetVisibilityHidesAttributes() throws Exception {
        User master = createUser("auto_rpg_master_chk3", UserRole.RESIDENT);
        MockHttpSession session = login("auto_rpg_master_chk3");

        // attributesVisible не передаём — как это делает браузер при снятой галочке,
        // полагаясь на скрытое поле false из формы.
        mockMvc.perform(post("/rpg/games").session(session).with(csrf())
                        .param("title", "Игра со скрытой анкетой")
                        .param("description", "Анкета скрыта от игроков.")
                        .param("system", "DND")
                        .param("visibility", "OPEN")
                        .param("status", "RECRUITING")
                        .param("attributesVisible", "false"))
                .andExpect(status().is3xxRedirection());

        Game created = gameRepository.findAll().stream()
                .filter(g -> "Игра со скрытой анкетой".equals(g.getTitle()))
                .findFirst().orElseThrow();
        assertThat(created.isAttributesVisible()).isFalse();
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.columns WHERE table_name = ? AND column_name = ?",
            Integer.class, table, column);
        return count != null && count > 0;
    }

    /** Карточка системы по её ID в HTML списка библиотеки. */
    private String cardWithId(String html, Long systemId) {
        int start = html.indexOf("card h-100", html.indexOf("href=\"/rpg/systems/" + systemId + "\"") - 4000);
        int end = html.indexOf("</div>\n            </div>", start);
        return html.substring(Math.max(start, 0), end > start ? end : start + 2000);
    }

    @Test
    void positionFieldIsGoneAndOrderComesFromRowSequence() throws Exception {
        User author = createUser("auto_rpg_master_nopos", UserRole.RESIDENT);
        MockHttpSession session = login("auto_rpg_master_nopos");

        String form = mockMvc.perform(get("/rpg/systems/new").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        // Автору порядок задавать не нужно: сервер проставляет его по номеру строки.
        assertThat(form).doesNotContain("Порядок");
        assertThat(form).doesNotContain("data-attr-position");
        assertThat(form).doesNotContain("data-new-position");
        assertThat(form).doesNotContain("templateAttributes[0].position");

        // Значение, присланное вручную, игнорируется: порядок определяется строками.
        mockMvc.perform(post("/rpg/systems/new").session(session).with(csrf())
                        .param("name", "Система без поля порядка")
                        .param("templateAttributes[0].name", "Мана")
                        .param("templateAttributes[0].type", "NUMERIC")
                        .param("templateAttributes[0].position", "999")
                        .param("templateAttributes[1].name", "Заклинания")
                        .param("templateAttributes[1].type", "LIST")
                        .param("templateAttributes[1].listKind", "COMPLEX"))
                .andExpect(status().is3xxRedirection());

        GameSystemDefinition created = definitionRepository.findAll().stream()
                .filter(sys -> "Система без поля порядка".equals(sys.getName()))
                .findFirst().orElseThrow();
        assertThat(created.getTemplateAttributeList()).hasSize(2);
        assertThat(created.getTemplateAttributeList().get(0).getName()).isEqualTo("Мана");
        assertThat(created.getTemplateAttributeList().get(0).getPosition()).isZero();
        assertThat(created.getTemplateAttributeList().get(1).getName()).isEqualTo("Заклинания");
        assertThat(created.getTemplateAttributeList().get(1).getPosition()).isEqualTo(10);

        // В таблице состава на странице системы колонки порядка тоже нет.
        Long id = created.getId();
        String view = mockMvc.perform(get("/rpg/systems/" + id).session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(view).doesNotContain("Порядок");
        assertThat(view).contains("Мана");
        assertThat(view).contains("Заклинания");
    }

    @Test
    void systemsListLinksToGames() throws Exception {
        createUser("auto_rpg_master_back", UserRole.RESIDENT);
        String page = mockMvc.perform(get("/rpg/systems").session(login("auto_rpg_master_back")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(page).contains("href=\"/rpg/games\"");
        assertThat(page).contains("К играм");
        assertThat(page).doesNotContain("К созданию игр");
        assertThat(page).doesNotContain("href=\"/rpg/games/new\" class=\"btn btn-outline-secondary\"");
    }

    @Test
    void listEditEndpointsAreNotAvailable() throws Exception {
        User master = createUser("auto_rpg_master_noep", UserRole.RESIDENT);
        createUser("auto_rpg_player_noep", UserRole.RESIDENT);
        Game game = createGame(master, "Эндпоинты", "Правки только через форму.", GameSystem.DND, GameVisibility.OPEN, GameStatus.RECRUITING);
        Long playerId = applyFor("auto_rpg_player_noep", game, "Герой");
        acceptFor(game, playerId);
        Long sheetId = sheetIdFor(game, playerId);
        MockHttpSession session = login("auto_rpg_player_noep");

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/characters/" + sheetId + "/lists/inventory/add")
                        .session(session).with(csrf())
                        .param("header", "Факел"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/characters/" + sheetId + "/lists/inventory/remove")
                        .session(session).with(csrf())
                        .param("itemIndex", "0"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listJsonFromEditFormIsPersistedAndRenderedGroupedByLevel() throws Exception {
        User master = createUser("auto_rpg_master_edit", UserRole.RESIDENT);
        createUser("auto_rpg_player_edit", UserRole.RESIDENT);
        Game game = createGame(master, "Форма списков", "Сохранение из формы.", GameSystem.DND, GameVisibility.OPEN, GameStatus.RECRUITING);
        Long playerId = applyFor("auto_rpg_player_edit", game, "Ученик");
        acceptFor(game, playerId);
        Long sheetId = sheetIdFor(game, playerId);
        MockHttpSession session = login("auto_rpg_player_edit");
        int spellsIndex = attributeIndex(sheetId, "spells");

        String listJson = "[{\"header\":\"Щит\",\"description\":\"AC +2\",\"level\":1},"
                + "{\"header\":\"Молния\",\"description\":\"Урон 8d6\",\"level\":3}]";

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/characters/" + sheetId + "/edit")
                        .session(session).with(csrf())
                        .param("name", "Ученик")
                        .param("attributes[" + spellsIndex + "].key", "spells")
                        .param("attributes[" + spellsIndex + "].type", "LIST")
                        .param("attributes[" + spellsIndex + "].listJson", listJson))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/rpg/games/" + game.getId() + "/characters/" + sheetId));

        String page = sheetPage(game, sheetId, "auto_rpg_player_edit");
        assertThat(page).contains("Щит").contains("AC +2").contains("Молния").contains("Урон 8d6");
        assertThat(page.indexOf("Ур. 1")).isLessThan(page.indexOf("Ур. 3"));
    }

    @Test
    void spellSlotsCanBeConfiguredForAnyLevelFromEditForm() throws Exception {
        User master = createUser("auto_rpg_master_cfg", UserRole.RESIDENT);
        createUser("auto_rpg_player_cfg", UserRole.RESIDENT);
        Game game = createGame(master, "Настройка ячеек", "Любой уровень 1-9.", GameSystem.DND, GameVisibility.OPEN, GameStatus.RECRUITING);
        Long playerId = applyFor("auto_rpg_player_cfg", game, "Чародей");
        acceptFor(game, playerId);
        Long sheetId = sheetIdFor(game, playerId);
        MockHttpSession session = login("auto_rpg_player_cfg");

        // Уровня 5 в листе ещё нет (0 / 0) — задаём его из формы редактирования.
        int maxIdx = attributeIndex(sheetId, "spell_slot_max:5");
        int curIdx = attributeIndex(sheetId, "spell_slot_current:5");

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/characters/" + sheetId + "/edit")
                        .session(session).with(csrf())
                        .param("name", "Чародей")
                        .param("attributes[" + maxIdx + "].key", "spell_slot_max:5")
                        .param("attributes[" + maxIdx + "].type", "NUMERIC")
                        .param("attributes[" + maxIdx + "].numericValue", "2")
                        .param("attributes[" + curIdx + "].key", "spell_slot_current:5")
                        .param("attributes[" + curIdx + "].type", "NUMERIC")
                        .param("attributes[" + curIdx + "].numericValue", "2"))
                .andExpect(status().is3xxRedirection());

        assertThat(numericOf(sheetId, "spell_slot_max:5")).isEqualTo(2);
        assertThat(numericOf(sheetId, "spell_slot_current:5")).isEqualTo(2);

        String page = sheetPage(game, sheetId, "auto_rpg_player_cfg");
        assertThat(page).contains("Ур. 5");
        assertThat(page).doesNotContain("Ур. 6");
    }

    @Test
    void resetSpellSlotsSetsCurrentEqualToMaxAndPersists() throws Exception {
        User master = createUser("auto_rpg_master_reset", UserRole.RESIDENT);
        createUser("auto_rpg_player_reset", UserRole.RESIDENT);
        Game game = createGame(master, "Сброс ячеек", "Текущее = максимальному.", GameSystem.DND, GameVisibility.OPEN, GameStatus.RECRUITING);
        Long playerId = applyFor("auto_rpg_player_reset", game, "Маг");
        acceptFor(game, playerId);
        Long sheetId = sheetIdFor(game, playerId);
        setNumeric(sheetId, "spell_slot_max:1", 4);
        setNumeric(sheetId, "spell_slot_current:1", 1);
        MockHttpSession session = login("auto_rpg_player_reset");

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/characters/" + sheetId + "/spell-slots-reset")
                        .session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/rpg/games/" + game.getId() + "/characters/" + sheetId));

        assertThat(numericOf(sheetId, "spell_slot_current:1")).isEqualTo(4);
    }

    @Test
    void acceptingApplicationMakesPlayerVisibleAndCounted() throws Exception {
        User master = createUser("auto_rpg_master_5", UserRole.RESIDENT);
        User player = createUser("auto_rpg_player_5", UserRole.RESIDENT);
        Game game = createGameFor(master, "Хроники", GameSystem.VERBAL, GameVisibility.OPEN);
        Long playerId = applyFor("auto_rpg_player_5", game, "Торин");

        acceptFor(game, playerId);

        assertThat(applicationRepository.existsByGameIdAndUserIdAndStatus(game.getId(), playerId, ApplicationStatus.ACCEPTED))
                .isTrue();

        MockHttpSession playerSession = login("auto_rpg_player_5");
        mockMvc.perform(get("/rpg/games/" + game.getId()).session(playerSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Вы в игре")))
                .andExpect(content().string(containsString("Редактировать лист")));

        MockHttpSession masterSession = login("auto_rpg_master_5");
        String masterPage = mockMvc.perform(get("/rpg/games/" + game.getId()).session(masterSession))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(masterPage).contains("Торин");
        assertThat(masterPage).contains("Игроки");
    }

    @Test
    void applyingUpdatesExistingSheet() throws Exception {
        User master = createUser("auto_rpg_master_6", UserRole.RESIDENT);
        createUser("auto_rpg_player_6", UserRole.RESIDENT);
        Game game = createGame(master, "Редактирование", "Обновляем лист.", GameSystem.VERBAL, GameVisibility.OPEN, GameStatus.RECRUITING);
        MockHttpSession session = login("auto_rpg_player_6");

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/apply").session(session).with(csrf())
                        .param("name", "Исходное"))
                .andExpect(status().is3xxRedirection());
        Long playerId = userRepository.findByUsername("auto_rpg_player_6").orElseThrow().getId();
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
        User master = createUser("auto_rpg_master_7", UserRole.RESIDENT);
        Game game = createGame(master, "Своя игра", "Ведущий не может подавать.", GameSystem.VERBAL, GameVisibility.OPEN, GameStatus.RECRUITING);
        MockHttpSession session = login("auto_rpg_master_7");

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/apply").session(session).with(csrf())
                        .param("name", "Попытка"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Нельзя подать заявку на свою игру")));
    }

    @Test
    void masterCreatesNpcs() throws Exception {
        User master = createUser("auto_rpg_master_8", UserRole.RESIDENT);
        Game game = createGame(master, "NPC-тест", "Нужны NPC.", GameSystem.VERBAL, GameVisibility.OPEN, GameStatus.RECRUITING);
        MockHttpSession session = login("auto_rpg_master_8");

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
        User master = createUser("auto_rpg_master_apl", UserRole.RESIDENT);
        User p1 = createUser("auto_rpg_player_apl1", UserRole.RESIDENT);
        User p2 = createUser("auto_rpg_player_apl2", UserRole.RESIDENT);
        Game game = createGameFor(master, "Заявки-лист", GameSystem.VERBAL, GameVisibility.OPEN);
        applyFor("auto_rpg_player_apl1", game, "Первый");
        applyFor("auto_rpg_player_apl2", game, "Второй");

        MockHttpSession masterSession = login("auto_rpg_master_apl");
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
        User master = createUser("auto_rpg_master_9", UserRole.RESIDENT);
        User player = createUser("auto_rpg_player_9", UserRole.RESIDENT);
        Game game = createGameFor(master, "Комнаты-тест", GameSystem.VERBAL, GameVisibility.OPEN);
        applyFor("auto_rpg_player_9", game, "Воин");
        acceptFor(game, player.getId());

        MvcResult roomRes = mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms")
                        .session(login("auto_rpg_master_9")).with(csrf())
                        .param("title", "Таверна")
                        .param("privateRoom", "false"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        Long roomId = Long.parseLong(roomRes.getResponse().getRedirectedUrl().replaceAll(".*rooms/", ""));

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms/" + roomId + "/posts")
                        .session(login("auto_rpg_player_9")).with(csrf())
                        .param("content", "Разведка боем.")
                        .param("die", "d6", "d20")
                        .param("count", "2", "2000")
                        .param("modifier", "1", "0")
                        .param("rollComment", "Атака", "Инциатива"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/rpg/games/" + game.getId() + "/rooms/" + roomId));

        mockMvc.perform(get("/rpg/games/" + game.getId() + "/rooms/" + roomId)
                        .session(login("auto_rpg_player_9")))
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
        User master = createUser("auto_rpg_master_10", UserRole.RESIDENT);
        User stranger = createUser("auto_rpg_stranger_1", UserRole.RESIDENT);
        Game game = createGameFor(master, "Закрытые комнаты", GameSystem.VERBAL, GameVisibility.OPEN);

        MvcResult roomRes = mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms")
                        .session(login("auto_rpg_master_10")).with(csrf())
                        .param("title", "Дом кузнеца"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        Long roomId = Long.parseLong(roomRes.getResponse().getRedirectedUrl().replaceAll(".*rooms/", ""));

        MockHttpSession strSession = login("auto_rpg_stranger_1");
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
        User master = createUser("auto_rpg_master_11", UserRole.RESIDENT);
        User player = createUser("auto_rpg_player_11", UserRole.RESIDENT);
        Game game = createGameFor(master, "Приватные", GameSystem.VERBAL, GameVisibility.OPEN);
        applyFor("auto_rpg_player_11", game, "Наблюдатель");
        acceptFor(game, player.getId());

        MvcResult roomRes = mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms")
                        .session(login("auto_rpg_master_11")).with(csrf())
                        .param("title", "Тайная комната")
                        .param("privateRoom", "true"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        Long roomId = Long.parseLong(roomRes.getResponse().getRedirectedUrl().replaceAll(".*rooms/", ""));

        MockHttpSession pSession = login("auto_rpg_player_11");
        String gamePage = mockMvc.perform(get("/rpg/games/" + game.getId()).session(pSession))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(gamePage).contains("Тайная комната");
        assertThat(gamePage).contains("bi-lock-fill");
        assertThat(gamePage).doesNotContain("/rooms/" + roomId);
        mockMvc.perform(get("/rpg/games/" + game.getId() + "/rooms/" + roomId).session(pSession))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms/" + roomId + "/members")
                        .session(login("auto_rpg_master_11")).with(csrf())
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
        User master = createUser("auto_rpg_master_12", UserRole.RESIDENT);
        User player = createUser("auto_rpg_player_12", UserRole.RESIDENT);
        User viewer = createUser("auto_rpg_viewer_12", UserRole.RESIDENT);
        Game game = createGameFor(master, "DND-тест", GameSystem.DND, GameVisibility.OPEN);
        applyFor("auto_rpg_player_12", game, "Варвар");
        Long playerId = userRepository.findByUsername("auto_rpg_player_12").orElseThrow().getId();
        acceptFor(game, playerId);
        var app = applicationRepository.findByGameIdAndUserId(game.getId(), playerId).orElseThrow();
        Long sheetId = app.getSheetId();

        MockHttpSession viewerSession = login("auto_rpg_viewer_12");
        // По умолчанию (attributesVisible=true) все, кто имеет доступ к игре, видят все атрибуты.
        mockMvc.perform(get("/rpg/games/" + game.getId() + "/characters/" + sheetId).session(viewerSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Сила")));

        // Ведущий выключает чекбокс «игроки видят все параметры» в настройках игры.
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/edit").session(login("auto_rpg_master_12")).with(csrf())
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
        mockMvc.perform(get("/rpg/games/" + game.getId() + "/characters/" + sheetId).session(login("auto_rpg_master_12")))
                .andExpect(content().string(containsString("<span>Сила</span>")));
        mockMvc.perform(get("/rpg/games/" + game.getId() + "/characters/" + sheetId).session(login("auto_rpg_player_12")))
                .andExpect(content().string(containsString("<span>Сила</span>")));
    }

    @Test
    void npcSheetsVisibleToPlayers() throws Exception {
        User master = createUser("auto_rpg_master_n1", UserRole.RESIDENT);
        User player = createUser("auto_rpg_player_n1", UserRole.RESIDENT);
        Game game = createGameFor(master, "NPC-игроки", GameSystem.DND, GameVisibility.OPEN);
        applyFor("auto_rpg_player_n1", game, "Следопыт");
        Long playerId = userRepository.findByUsername("auto_rpg_player_n1").orElseThrow().getId();
        acceptFor(game, playerId);

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/npc").session(login("auto_rpg_master_n1")).with(csrf())
                        .param("name", "Трактирщик Багман")
                        .param("kventa", "Хозяин «Сломанной подковы»."))
                .andExpect(status().is3xxRedirection());
        Long npcId = sheetRepository.findByGameIdAndNpcOrderByCreatedAtDesc(game.getId(), true).get(0).getId();

        MockHttpSession playerSession = login("auto_rpg_player_n1");
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
        User master = createUser("auto_rpg_master_13", UserRole.RESIDENT);
        Game game = createGameFor(master, "NPC-посты", GameSystem.DND, GameVisibility.OPEN);

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/npc").session(login("auto_rpg_master_13")).with(csrf())
                        .param("name", "Стражник Гора")
                        .param("kventa", "Немой страж ворот."))
                .andExpect(status().is3xxRedirection());
        Long npcId = sheetRepository.findByGameIdAndNpcOrderByCreatedAtDesc(game.getId(), true).get(0).getId();

        MvcResult roomRes = mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms")
                        .session(login("auto_rpg_master_13")).with(csrf())
                        .param("title", "Ворота"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        Long roomId = Long.parseLong(roomRes.getResponse().getRedirectedUrl().replaceAll(".*rooms/", ""));

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms/" + roomId + "/posts")
                        .session(login("auto_rpg_master_13")).with(csrf())
                        .param("content", "Кто идёт?")
                        .param("sheetId", String.valueOf(npcId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/rpg/games/" + game.getId() + "/rooms/" + roomId));

        RoomPost post = roomPostRepository.findByRoomIdOrderByCreatedAtAsc(roomId).get(0);
        assertThat(post.getSheetId()).isEqualTo(npcId);

        mockMvc.perform(get("/rpg/games/" + game.getId() + "/rooms/" + roomId).session(login("auto_rpg_master_13")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Кто идёт?")))
                .andExpect(content().string(containsString("Стражник Гора")));
    }

    @Test
    void postWithImageAttachment() throws Exception {
        User master = createUser("auto_rpg_master_14", UserRole.RESIDENT);
        Game game = createGameFor(master, "Вложения", GameSystem.VERBAL, GameVisibility.OPEN);

        MvcResult roomRes = mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms")
                        .session(login("auto_rpg_master_14")).with(csrf())
                        .param("title", "Склеп"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        Long roomId = Long.parseLong(roomRes.getResponse().getRedirectedUrl().replaceAll(".*rooms/", ""));

        MockMultipartFile image = new MockMultipartFile("files", "карта.zip", "image/png", new byte[]{1, 2, 3});
        mockMvc.perform(multipart("/rpg/games/" + game.getId() + "/rooms/" + roomId + "/posts")
                        .file(image)
                        .session(login("auto_rpg_master_14")).with(csrf())
                        .param("content", "Карта склепа"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/rpg/games/" + game.getId() + "/rooms/" + roomId));

        RoomPost post = roomPostRepository.findByRoomIdOrderByCreatedAtAsc(roomId).get(0);
        var attachments = postAttachmentRepository.findByPostIdOrderByIdAsc(post.getId());
        assertThat(attachments).hasSize(1);
        assertThat(attachments.get(0).getContentType()).isEqualTo("image/png");
        assertThat(attachments.get(0).getFileName()).isEqualTo("карта.zip");

        mockMvc.perform(get("/rpg/games/" + game.getId() + "/rooms/" + roomId).session(login("auto_rpg_master_14")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Карта склепа")))
                .andExpect(content().string(containsString("img-thumbnail")))
                .andExpect(content().string(containsString("/attachments/" + attachments.get(0).getId())));

        mockMvc.perform(get("/rpg/games/" + game.getId() + "/rooms/" + roomId
                        + "/attachments/" + attachments.get(0).getId()).session(login("auto_rpg_master_14")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG));
    }

    @Test
    void savedRollScopedToGame() throws Exception {
        User master = createUser("auto_rpg_master_15", UserRole.RESIDENT);
        Game game = createGameFor(master, "Избранное-игра", GameSystem.VERBAL, GameVisibility.OPEN);

        MvcResult roomRes = mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms")
                        .session(login("auto_rpg_master_15")).with(csrf())
                        .param("title", "Комната"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        Long roomId = Long.parseLong(roomRes.getResponse().getRedirectedUrl().replaceAll(".*rooms/", ""));

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms/" + roomId + "/saved-rolls")
                        .session(login("auto_rpg_master_15")).with(csrf())
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

        mockMvc.perform(get("/rpg/games/" + game.getId() + "/rooms/" + roomId).session(login("auto_rpg_master_15")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ambush")));
    }

    @Test
    void npcFavoriteScopedToSheetAndDeduped() throws Exception {
        User master = createUser("auto_rpg_master_16", UserRole.RESIDENT);
        Game game = createGameFor(master, "Избранное-NPC", GameSystem.DND, GameVisibility.OPEN);

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/npc").session(login("auto_rpg_master_16")).with(csrf())
                        .param("name", "Трактирщик Гобры"))
                .andExpect(status().is3xxRedirection());
        Long npcId = sheetRepository.findByGameIdAndNpcOrderByCreatedAtDesc(game.getId(), true).get(0).getId();

        MvcResult roomRes = mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms")
                        .session(login("auto_rpg_master_16")).with(csrf())
                        .param("title", "Комната"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        Long roomId = Long.parseLong(roomRes.getResponse().getRedirectedUrl().replaceAll(".*rooms/", ""));

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms/" + roomId + "/posts")
                        .session(login("auto_rpg_master_16")).with(csrf())
                        .param("content", "Личное")
                        .param("die", "d20").param("count", "1").param("modifier", "1").param("rollComment", "Удар"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms/" + roomId + "/posts")
                        .session(login("auto_rpg_master_16")).with(csrf())
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
                        .session(login("auto_rpg_master_16")).with(csrf())
                        .param("postId", String.valueOf(ownPostId))
                        .param("die", "d20").param("count", "1").param("modifier", "1").param("rollComment", "Удар"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));

        // Пост NPC → вложенное «Избранное» NPC; повторная попытка не дублируется.
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms/" + roomId + "/saved-rolls")
                        .session(login("auto_rpg_master_16")).with(csrf())
                        .param("postId", String.valueOf(npcPostId))
                        .param("die", "d20").param("count", "1").param("modifier", "2").param("rollComment", "Рык"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms/" + roomId + "/saved-rolls")
                        .session(login("auto_rpg_master_16")).with(csrf())
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
                        .session(login("auto_rpg_master_16")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
        assertThat(savedRollRepository.count()).isEqualTo(1);
    }

    @Test
    void gameCreateOpenWithGuestAccessBecomesGuest() throws Exception {
        User resident = createUser("auto_rpg_master_16", UserRole.RESIDENT);
        MockHttpSession session = login("auto_rpg_master_16");

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
        User master = createUser("auto_rpg_master_17", UserRole.RESIDENT);
        User friend = createUser("auto_rpg_friend_17", UserRole.RESIDENT);
        MockHttpSession session = login("auto_rpg_master_17");

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
        User master = createUser("auto_rpg_master_18", UserRole.RESIDENT);
        Game game = createGameFor(master, "Приват-создание", GameSystem.VERBAL, GameVisibility.OPEN);
        User friend = createUser("auto_rpg_friend_18", UserRole.RESIDENT);

        MvcResult roomRes = mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms")
                        .session(login("auto_rpg_master_18")).with(csrf())
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
        User master = createUser("auto_rpg_master_19", UserRole.RESIDENT);
        Game game = createGameFor(master, "Подписи", GameSystem.DND, GameVisibility.OPEN);

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/npc").session(login("auto_rpg_master_19")).with(csrf())
                        .param("name", "Стражник Гора")
                        .param("kventa", "Немой страж ворот."))
                .andExpect(status().is3xxRedirection());
        Long npcId = sheetRepository.findByGameIdAndNpcOrderByCreatedAtDesc(game.getId(), true).get(0).getId();

        MvcResult roomRes = mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms")
                        .session(login("auto_rpg_master_19")).with(csrf())
                        .param("title", "Ворота"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        Long roomId = Long.parseLong(roomRes.getResponse().getRedirectedUrl().replaceAll(".*rooms/", ""));

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms/" + roomId + "/posts")
                        .session(login("auto_rpg_master_19")).with(csrf())
                        .param("content", "Ворота заперты."))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms/" + roomId + "/posts")
                        .session(login("auto_rpg_master_19")).with(csrf())
                        .param("content", "Кто идёт?")
                        .param("sheetId", String.valueOf(npcId)))
                .andExpect(status().is3xxRedirection());

        User player = createUser("auto_rpg_player_19", UserRole.RESIDENT);
        applyFor("auto_rpg_player_19", game, "Геральт");
        acceptFor(game, player.getId());
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms/" + roomId + "/posts")
                        .session(login("auto_rpg_player_19")).with(csrf())
                        .param("content", "Я Геральт, ищу грош."))
                .andExpect(status().is3xxRedirection());

        MvcResult pageRes = mockMvc.perform(get("/rpg/games/" + game.getId() + "/rooms/" + roomId)
                        .session(login("auto_rpg_master_19")))
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
        User master = createUser("auto_rpg_master_20", UserRole.RESIDENT);
        Game game = createGameFor(master, "Анкета-редактирование", GameSystem.DND, GameVisibility.OPEN);
        createUser("auto_rpg_player_20", UserRole.RESIDENT);

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/apply").session(login("auto_rpg_player_20")).with(csrf())
                        .param("name", "Эльфийка")
                        .param("kventa", "Странница."))
                .andExpect(status().is3xxRedirection());

        Long playerId = userRepository.findByUsername("auto_rpg_player_20").orElseThrow().getId();
        Long sheetId = applicationRepository.findByGameIdAndUserId(game.getId(), playerId).orElseThrow().getSheetId();
        Map<String, SheetAttribute> attrs = attributeRepository.findBySheetIdOrderByPositionAsc(sheetId).stream()
                .collect(Collectors.toMap(SheetAttribute::getKey, Function.identity()));
        attrs.get("class").setPosition(410);
        attrs.get("race").setPosition(420);
        attributeRepository.saveAll(attrs.values());

        MvcResult pageRes = mockMvc.perform(get("/rpg/games/" + game.getId() + "/apply")
                        .session(login("auto_rpg_player_20")))
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
        User master = createUser("auto_rpg_master_wl2", UserRole.RESIDENT);
        User invited1 = createUser("auto_rpg_wl_invite_1", UserRole.RESIDENT);
        User invited2 = createUser("auto_rpg_wl_invite_2", UserRole.RESIDENT);
        User outsider = createUser("auto_rpg_wl_outsider", UserRole.RESIDENT);
        Game game = createGame(master, "Закрытая-WL", "Описание.", GameSystem.VERBAL, GameVisibility.CLOSED, GameStatus.RECRUITING);
        MockHttpSession masterSession = login("auto_rpg_master_wl2");
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
        assertThat(page).contains("data-wl-user=\"auto_rpg_wl_invite_1\"");
        assertThat(page).contains("data-wl-user=\"auto_rpg_wl_invite_2\"");
        assertThat(page).doesNotContain("data-wl-user=\"auto_rpg_wl_outsider\"");
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
        User master = createUser("auto_rpg_master_custom2", UserRole.RESIDENT);
        User player = createUser("auto_rpg_player_custom2", UserRole.RESIDENT);
        Game game = createGame(master, "Кастом-правила", "Своя система.", GameSystem.CUSTOM, GameVisibility.OPEN, GameStatus.RECRUITING);
        MockHttpSession masterSession = login("auto_rpg_master_custom2");

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

        MockHttpSession playerSession = login("auto_rpg_player_custom2");
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
        User master = createUser("auto_rpg_master_roompub", UserRole.RESIDENT);
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
        User master = createUser("auto_rpg_master_rmkind", UserRole.RESIDENT);
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

    /**
     * Страница листа персонажа в режиме просмотра.
     */
    private String sheetPage(Game game, Long sheetId, String username) throws Exception {
        return mockMvc.perform(get("/rpg/games/" + game.getId() + "/characters/" + sheetId)
                        .session(login(username)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private Long sheetIdFor(Game game, Long playerId) {
        return applicationRepository.findByGameIdAndUserId(game.getId(), playerId).orElseThrow().getSheetId();
    }

    /**
     * Проставляет числовое значение параметра листа напрямую в БД.
     */
    private void setNumeric(Long sheetId, String key, int value) {
        SheetAttribute attr = attributeRepository.findBySheetIdAndKey(sheetId, key).orElseThrow();
        attr.setNumericValue(value);
        attributeRepository.save(attr);
    }

    private void setListKind(Long sheetId, String key, String listKind) {
        SheetAttribute attr = attributeRepository.findBySheetIdAndKey(sheetId, key).orElseThrow();
        attr.setListKind(org.corpsman.rpg.model.ListKind.valueOf(listKind));
        attributeRepository.save(attr);
    }

    private void setListValues(Long sheetId, String key, String json) {
        SheetAttribute attr = attributeRepository.findBySheetIdAndKey(sheetId, key).orElseThrow();
        attr.setListValues(json);
        attributeRepository.save(attr);
    }

    private Integer numericOf(Long sheetId, String key) {
        return attributeRepository.findBySheetIdAndKey(sheetId, key).orElseThrow().getNumericValue();
    }

    private String listValues(Long sheetId, String key) {
        return attributeRepository.findBySheetIdAndKey(sheetId, key).orElseThrow().getListValues();
    }

    private String listKindOf(Long sheetId, String key) {
        return String.valueOf(attributeRepository.findBySheetIdAndKey(sheetId, key).orElseThrow().getListKind());
    }

    private Long gameIdByTitle(String title) {
        return gameRepository.findAll().stream()
                .filter(g -> title.equals(g.getTitle()))
                .map(Game::getId)
                .findFirst()
                .orElse(null);
    }

    private Long createLibrarySystemWithTemplate(User author, String name) {
        GameSystemDefinition system = new GameSystemDefinition();
        system.setName(name);
        system.setDescription("Система с составом параметров.");
        system.setAuthorId(author.getId());
        system.setPublic(true);
        List<SystemAttributeDto> template = new ArrayList<>();
        template.add(attr("Мана", AttributeType.NUMERIC, 0, null));
        template.add(attr("Заклинания", AttributeType.LIST, 10, ListKind.COMPLEX));
        template.add(attr("Инвентарь", AttributeType.LIST, 20, ListKind.SIMPLE));
        system.setTemplateAttributeList(template);
        return definitionRepository.save(system).getId();
    }

    private SystemAttributeDto attr(String name, AttributeType type, int position, ListKind listKind) {
        SystemAttributeDto dto = new SystemAttributeDto();
        dto.setName(name);
        dto.setType(type);
        dto.setPosition(position);
        dto.setListKind(listKind);
        return dto;
    }

    private Long createLibrarySystem(User author, String name) {
        GameSystemDefinition system = new GameSystemDefinition();
        system.setName(name);
        system.setDescription("Описание для теста.");
        system.setAuthorId(author.getId());
        system.setPublic(true);
        return definitionRepository.save(system).getId();
    }

    /**
     * Индекс параметра в отсортированном списке атрибутов — он же индекс в
     * списке attributes формы редактирования.
     */
    private int attributeIndex(Long sheetId, String key) {
        List<SheetAttribute> attrs = attributeRepository.findBySheetIdOrderByPositionAsc(sheetId);
        for (int i = 0; i < attrs.size(); i++) {
            if (key.equals(attrs.get(i).getKey())) {
                return i;
            }
        }
        throw new AssertionError("Атрибут " + key + " не найден");
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