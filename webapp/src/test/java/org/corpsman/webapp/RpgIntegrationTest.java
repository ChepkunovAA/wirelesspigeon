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
import org.corpsman.rpg.service.character.CustomCharacterSystem;
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
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
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
    private org.springframework.transaction.PlatformTransactionManager txManager;

    @Autowired
    private EntityManager entityManager;

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
    private org.corpsman.forum.service.NotificationService notificationService;

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
    void customNumericAttributeKeepsBoundsStepAndAppliesDefault() throws Exception {
        User master = createUser("auto_rpg_master_cnum", UserRole.RESIDENT);
        Game game = createGameFor(master, "Числовые ограничения", GameSystem.CUSTOM, GameVisibility.OPEN);
        MockHttpSession session = login("auto_rpg_master_cnum");

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/attributes").session(session).with(csrf())
                        .param("name", "Сила воли")
                        .param("attributeType", "NUMERIC")
                        .param("defaultValue", "5")
                        .param("minValue", "1")
                        .param("maxValue", "10")
                        .param("stepValue", "2"))
                .andExpect(status().is3xxRedirection());

        CustomAttribute attr = customAttributeRepository.findByGameIdOrderByPositionAsc(game.getId()).get(0);
        assertThat(attr.getDefaultValue()).isEqualTo(5);
        assertThat(attr.getMinValue()).isEqualTo(1);
        assertThat(attr.getMaxValue()).isEqualTo(10);
        assertThat(attr.getStepValue()).isEqualTo(2);
        // Ручной ввод по умолчанию снят.
        assertThat(attr.isManualInputAllowed()).isFalse();

        // Значение по умолчанию попадает в новый лист.
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/npc").session(session).with(csrf())
                        .param("name", "Страж"))
                .andExpect(status().is3xxRedirection());
        Long sheetId = sheetRepository.findByGameIdOrderByCreatedAtDesc(game.getId()).get(0).getId();
        assertThat(numericOf(sheetId, "custom:" + attr.getId())).isEqualTo(5);
    }

    @Test
    void customAttributeFormRejectsInconsistentNumericBounds() throws Exception {
        User master = createUser("auto_rpg_master_cnum2", UserRole.RESIDENT);
        Game game = createGameFor(master, "Плохие границы", GameSystem.CUSTOM, GameVisibility.OPEN);
        MockHttpSession session = login("auto_rpg_master_cnum2");

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/attributes").session(session).with(csrf())
                        .param("name", "Сломанное поле")
                        .param("attributeType", "NUMERIC")
                        .param("minValue", "10")
                        .param("maxValue", "1"))
                .andExpect(status().isBadRequest());
        assertThat(customAttributeRepository.findByGameIdOrderByPositionAsc(game.getId())).isEmpty();

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/attributes").session(session).with(csrf())
                        .param("name", "Значение не в границах")
                        .param("attributeType", "NUMERIC")
                        .param("defaultValue", "50")
                        .param("minValue", "1")
                        .param("maxValue", "10"))
                .andExpect(status().isBadRequest());
        assertThat(customAttributeRepository.findByGameIdOrderByPositionAsc(game.getId())).isEmpty();
    }

    @Test
    void librarySystemFormAllowsSettingParameterConstraints() throws Exception {
        User master = createUser("auto_rpg_master_libcons", UserRole.RESIDENT);
        MockHttpSession session = login("auto_rpg_master_libcons");

        String form = mockMvc.perform(get("/rpg/systems/new").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        // Для каждого параметра задаются ограничения своего типа.
        assertThat(form).contains("По умолчанию");
        assertThat(form).contains("Нижняя граница");
        assertThat(form).contains("Верхняя граница");
        assertThat(form).contains("Шаг");
        assertThat(form).contains("Варианты, по одному в строке");
        assertThat(form).contains("Разрешить ручной ввод");
        assertThat(form).contains("data-attr-default-value");
        assertThat(form).contains("data-attr-min-value");
        assertThat(form).contains("data-attr-max-value");
        assertThat(form).contains("data-attr-step-value");
        assertThat(form).contains("data-attr-options-text");
        assertThat(form).contains("data-attr-manual-input");
        assertThat(form).contains("data-new-default-value");
        assertThat(form).contains("data-new-options-text");
        assertThat(form).contains("data-new-manual-input");
    }

    @Test
    void systemParameterConstraintsAreStoredAndAppliedToNewGame() throws Exception {
        User master = createUser("auto_rpg_master_libcons2", UserRole.RESIDENT);
        MockHttpSession session = login("auto_rpg_master_libcons2");

        mockMvc.perform(post("/rpg/systems/new").session(session).with(csrf())
                        .param("name", "Магия с ограничениями")
                        .param("description", "Параметры с границами.")
                        .param("published", "true")
                        .param("templateAttributes[0].name", "Мана")
                        .param("templateAttributes[0].type", "NUMERIC")
                        .param("templateAttributes[0].defaultValue", "5")
                        .param("templateAttributes[0].minValue", "0")
                        .param("templateAttributes[0].maxValue", "30")
                        .param("templateAttributes[0].stepValue", "2")
                        .param("templateAttributes[0].manualInputAllowed", "true")
                        .param("templateAttributes[1].name", "Мировоззрение")
                        .param("templateAttributes[1].type", "TEXT")
                        .param("templateAttributes[1].defaultText", "Хаос")
                        .param("templateAttributes[1].optionsText", "Хаос\nЗакон")
                        .param("templateAttributes[1].manualInputAllowed", "false"))
                .andExpect(status().is3xxRedirection());

        Long systemId = definitionRepository.findAll().stream()
                .filter(s -> "Магия с ограничениями".equals(s.getName()))
                .map(GameSystemDefinition::getId)
                .findFirst()
                .orElseThrow();
        List<SystemAttributeDto> template = definitionRepository.findById(systemId).orElseThrow()
                .getTemplateAttributeList();
        assertThat(template).hasSize(2);
        SystemAttributeDto mana = template.get(0);
        assertThat(mana.getDefaultValue()).isEqualTo(5);
        assertThat(mana.getMinValue()).isZero();
        assertThat(mana.getMaxValue()).isEqualTo(30);
        assertThat(mana.getStepValue()).isEqualTo(2);
        assertThat(mana.isManualInputAllowed()).isTrue();
        SystemAttributeDto worldview = template.get(1);
        assertThat(worldview.getOptions()).containsExactly("Хаос", "Закон");
        assertThat(worldview.isManualInputAllowed()).isFalse();

        // Игра на этой системе получает те же ограничения в параметрах листа.
        mockMvc.perform(post("/rpg/games").session(session).with(csrf())
                        .param("title", "Игра по магии")
                        .param("description", "Описание игры.")
                        .param("system", "CUSTOM")
                        .param("customSystemId", String.valueOf(systemId))
                        .param("status", "RECRUITING")
                        .param("visibility", "OPEN"))
                .andExpect(status().is3xxRedirection());

        Long gameId = gameIdByTitle("Игра по магии");
        List<CustomAttribute> attrs = customAttributeRepository.findByGameIdOrderByPositionAsc(gameId);
        assertThat(attrs).hasSize(2);
        CustomAttribute manaAttr = attrs.stream().filter(a -> "Мана".equals(a.getName())).findFirst().orElseThrow();
        assertThat(manaAttr.getDefaultValue()).isEqualTo(5);
        assertThat(manaAttr.getMinValue()).isZero();
        assertThat(manaAttr.getMaxValue()).isEqualTo(30);
        assertThat(manaAttr.stepOrDefault()).isEqualTo(2);
        assertThat(manaAttr.isManualInputAllowed()).isTrue();
        CustomAttribute textAttr = attrs.stream()
                .filter(a -> "Мировоззрение".equals(a.getName())).findFirst().orElseThrow();
        assertThat(textAttr.getDefaultText()).isEqualTo("Хаос");
        assertThat(textAttr.getOptions()).containsExactly("Хаос", "Закон");
        assertThat(textAttr.isManualInputAllowed()).isFalse();
    }

    @Test
    void librarySystemRejectsInconsistentParameterConstraints() throws Exception {
        User master = createUser("auto_rpg_master_libcons3", UserRole.RESIDENT);
        MockHttpSession session = login("auto_rpg_master_libcons3");

        mockMvc.perform(post("/rpg/systems/new").session(session).with(csrf())
                        .param("name", "Сломанные границы")
                        .param("templateAttributes[0].name", "Мана")
                        .param("templateAttributes[0].type", "NUMERIC")
                        .param("templateAttributes[0].minValue", "10")
                        .param("templateAttributes[0].maxValue", "1"))
                .andExpect(status().isBadRequest());

        assertThat(definitionRepository.findAll().stream()
                .map(GameSystemDefinition::getName))
                .doesNotContain("Сломанные границы");

        // Текст без вариантов отклонять незачем: ручной ввод включается сам,
        // иначе поле было бы нечем заполнить.
        mockMvc.perform(post("/rpg/systems/new").session(session).with(csrf())
                        .param("name", "Без вариантов")
                        .param("templateAttributes[0].name", "Мировоззрение")
                        .param("templateAttributes[0].type", "TEXT"))
                .andExpect(status().is3xxRedirection());
        GameSystemDefinition freeText = definitionRepository.findByNameIgnoreCase("Без вариантов")
                .orElseThrow();
        SystemAttributeDto freeAttr = freeText.getTemplateAttributeList().get(0);
        assertThat(freeAttr.getOptions()).isEmpty();
        assertThat(freeAttr.isManualInputAllowed()).isTrue();
        assertThat(freeAttr.getTextMode()).isEqualTo(SystemAttributeDto.TextMode.FREE);
    }

    @Test
    void customTextWithoutOptionsBecomesFillableFreeField() throws Exception {
        User master = createUser("auto_rpg_master_ctext", UserRole.RESIDENT);
        Game game = createGameFor(master, "Текстовые ограничения", GameSystem.CUSTOM, GameVisibility.OPEN);
        MockHttpSession session = login("auto_rpg_master_ctext");

        // Поле без списка вариантов и без ручного ввода остаётся заполняемым:
        // ручной ввод включается принудительно.
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/attributes").session(session).with(csrf())
                        .param("name", "Стиль")
                        .param("attributeType", "TEXT")
                        .param("manualInputAllowed", "false"))
                .andExpect(status().is3xxRedirection());
        List<CustomAttribute> created = customAttributeRepository
                .findByGameIdOrderByPositionAsc(game.getId());
        assertThat(created).hasSize(1);
        assertThat(created.get(0).isManualInputAllowed()).isTrue();
        // Дальше по тесту создаются свои параметры, поэтому этот убираем.
        customAttributeRepository.delete(created.get(0));

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/attributes").session(session).with(csrf())
                        .param("name", "Стиль")
                        .param("attributeType", "TEXT")
                        .param("optionsText", "Огонь\nВода"))
                .andExpect(status().is3xxRedirection());
        CustomAttribute attr = customAttributeRepository.findByGameIdOrderByPositionAsc(game.getId()).get(0);
        assertThat(attr.getOptions()).containsExactly("Огонь", "Вода");

        // С ручным вводом варианты не обязательны.
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/attributes").session(session).with(csrf())
                        .param("name", "Прозвище")
                        .param("attributeType", "TEXT")
                        .param("manualInputAllowed", "true"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void customTextFieldShowsInputSelectOrSelectWithOtherDependingOnFlag() throws Exception {
        User master = createUser("auto_rpg_master_ctui", UserRole.RESIDENT);
        Game game = createGameFor(master, "Виды текстового поля", GameSystem.CUSTOM, GameVisibility.OPEN);
        MockHttpSession session = login("auto_rpg_master_ctui");
        String attributes = "/rpg/games/" + game.getId() + "/attributes";

        // Ручной ввод без предустановленных значений — обычное поле ввода.
        mockMvc.perform(post(attributes).session(session).with(csrf())
                        .param("name", "Прозвище")
                        .param("attributeType", "TEXT")
                        .param("manualInputAllowed", "true"))
                .andExpect(status().is3xxRedirection());
        // Ручной ввод со значениями — список с пунктом «Другое».
        mockMvc.perform(post(attributes).session(session).with(csrf())
                        .param("name", "Архетип")
                        .param("attributeType", "TEXT")
                        .param("optionsText", "Воин\nПлут")
                        .param("manualInputAllowed", "true"))
                .andExpect(status().is3xxRedirection());
        // Без ручного ввода — только выбор из списка.
        mockMvc.perform(post(attributes).session(session).with(csrf())
                        .param("name", "Стиль")
                        .param("attributeType", "TEXT")
                        .param("optionsText", "Огонь\nВода"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/npc").session(session).with(csrf())
                        .param("name", "Образец"))
                .andExpect(status().is3xxRedirection());
        Long sheetId = sheetRepository.findByGameIdOrderByCreatedAtDesc(game.getId()).get(0).getId();

        String form = mockMvc.perform(get("/rpg/games/" + game.getId() + "/characters/" + sheetId + "/edit")
                        .session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        // Обычное поле ввода: без списка и без «Другого».
        String plain = blockOf(form, "custom:" + nicknameId(game));
        assertThat(plain).contains("textValue");
        assertThat(plain).doesNotContain("data-opt-select");
        assertThat(plain).doesNotContain("__other__");

        // Список с пунктом «Другое» и полем ручного ввода.
        String withOther = blockOf(form, "custom:" + archetypeId(game));
        assertThat(withOther).contains("data-opt-select");
        assertThat(withOther).contains("__other__");
        assertThat(withOther).contains("data-opt-manual");

        // Только выбор: пункта «Другое» нет.
        String onlySelect = blockOf(form, "custom:" + styleId(game));
        assertThat(onlySelect).contains("data-opt-select");
        assertThat(onlySelect).doesNotContain("__other__");
    }

    /**
     * Фрагмент формы листа, относящийся к одному параметру: от скрытого поля
     * с ключом до следующего параметра.
     */
    private String blockOf(String html, String key) {
        // Ключ параметра выводится скрытым полем в конце его блока, поэтому
        // блок параметра — текст между ключом предыдущего и этим ключом.
        int end = html.indexOf("value=\"" + key + "\"");
        if (end < 0) {
            return "";
        }
        int start = html.lastIndexOf("value=\"custom:", end - 1);
        return html.substring(start < 0 ? Math.max(0, end - 2000) : start, end);
    }

    private Long nicknameId(Game game) {
        return attributeIdOf(game, "Прозвище");
    }

    private Long archetypeId(Game game) {
        return attributeIdOf(game, "Архетип");
    }

    private Long styleId(Game game) {
        return attributeIdOf(game, "Стиль");
    }

    private Long attributeIdOf(Game game, String name) {
        return customAttributeRepository.findByGameIdOrderByPositionAsc(game.getId()).stream()
                .filter(a -> name.equals(a.getName()))
                .findFirst().orElseThrow().getId();
    }

    @Test
    void customFieldValuesAreValidatedOnSheetSave() throws Exception {
        User master = createUser("auto_rpg_master_cvalid", UserRole.RESIDENT);
        Game game = createGameFor(master, "Проверка значений", GameSystem.CUSTOM, GameVisibility.OPEN);
        MockHttpSession session = login("auto_rpg_master_cvalid");

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/attributes").session(session).with(csrf())
                        .param("name", "Воля")
                        .param("attributeType", "NUMERIC")
                        .param("minValue", "1").param("maxValue", "10"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/attributes").session(session).with(csrf())
                        .param("name", "Стиль")
                        .param("attributeType", "TEXT")
                        .param("optionsText", "Огонь\nВода"))
                .andExpect(status().is3xxRedirection());
        List<CustomAttribute> attrs = customAttributeRepository.findByGameIdOrderByPositionAsc(game.getId());
        Long numericId = attrs.get(0).getId();
        Long textId = attrs.get(1).getId();

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/npc").session(session).with(csrf())
                        .param("name", "Ученик"))
                .andExpect(status().is3xxRedirection());
        Long sheetId = sheetRepository.findByGameIdOrderByCreatedAtDesc(game.getId()).get(0).getId();

        // Число вне границ отклоняется, значение не меняется.
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/characters/" + sheetId + "/edit")
                        .session(session).with(csrf())
                        .param("name", "Ученик")
                        .param("attributes[0].key", "custom:" + numericId)
                        .param("attributes[0].type", "NUMERIC")
                        .param("attributes[0].numericValue", "50")
                        .param("attributes[1].key", "custom:" + textId)
                        .param("attributes[1].type", "TEXT")
                        .param("attributes[1].textValue", "Огонь"))
                .andExpect(status().isBadRequest());
        // Значение из отклонённого запроса не сохранилось.
        assertThat(numericOf(sheetId, "custom:" + numericId)).isNull();

        // Значение не из списка при запрещённом ручном вводе отклоняется.
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/characters/" + sheetId + "/edit")
                        .session(session).with(csrf())
                        .param("name", "Ученик")
                        .param("attributes[0].key", "custom:" + numericId)
                        .param("attributes[0].type", "NUMERIC")
                        .param("attributes[0].numericValue", "7")
                        .param("attributes[1].key", "custom:" + textId)
                        .param("attributes[1].type", "TEXT")
                        .param("attributes[1].textValue", "Воздух"))
                .andExpect(status().isBadRequest());
        assertThat(textOf(sheetId, "custom:" + textId)).isNull();

        // Корректные значения сохраняются.
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/characters/" + sheetId + "/edit")
                        .session(session).with(csrf())
                        .param("name", "Ученик")
                        .param("attributes[0].key", "custom:" + numericId)
                        .param("attributes[0].type", "NUMERIC")
                        .param("attributes[0].numericValue", "7")
                        .param("attributes[1].key", "custom:" + textId)
                        .param("attributes[1].type", "TEXT")
                        .param("attributes[1].textValue", "Вода"))
                .andExpect(status().is3xxRedirection());
        assertThat(numericOf(sheetId, "custom:" + numericId)).isEqualTo(7);
        assertThat(textOf(sheetId, "custom:" + textId)).isEqualTo("Вода");
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
    void systemListRendersRowsWithActionsInsideRow() throws Exception {
        User master = createUser("auto_rpg_master_card", UserRole.RESIDENT);
        MockHttpSession session = login("auto_rpg_master_card");
        Long systemId = createLibrarySystem(master, "Система для строки");

        String page = mockMvc.perform(get("/rpg/systems").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        // Список строками, карточек больше нет.
        assertThat(page).contains("data-system-row=\"" + systemId + "\"");
        assertThat(page).contains("table table-hover align-middle");
        assertThat(page).doesNotContain("card h-100 shadow-sm");
        assertThat(page).doesNotContain("card-footer bg-transparent");
        assertThat(page).contains("Публичная");
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
        assertThat(page).doesNotContain("sheet-submit");
        assertThat(page).doesNotContain(" disabled");
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

    private int indexOf(String html, String needle) {
        return html.indexOf(needle);
    }

    private int countOccurrences(String html, String needle) {
        int count = 0;
        int pos = html.indexOf(needle);
        while (pos >= 0) {
            count++;
            pos = html.indexOf(needle, pos + needle.length());
        }
        return count;
    }

    private int countRows(String table, String where) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE " + where, Integer.class);
        return count == null ? 0 : count;
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.columns WHERE table_name = ? AND column_name = ?",
            Integer.class, table, column);
        return count != null && count > 0;
    }

    /** Карточка системы по её ID в HTML списка библиотеки. */
    /**
     * Фрагмент страницы библиотеки, относящийся к одной системе.
     */
    private String cardWithId(String html, Long systemId) {
        int start = html.indexOf("data-system-row=\"" + systemId + "\"");
        if (start < 0) {
            return "";
        }
        int end = html.indexOf("</tr>", start);
        return html.substring(start, end > start ? end : start + 1500);
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
    void manualInputFlagIsOfferedOnlyForValueFields() throws Exception {
        User master = createUser("auto_rpg_master_manual", UserRole.RESIDENT);
        MockHttpSession session = login("auto_rpg_master_manual");

        // Форма библиотеки: блок флага скрыт и показывается только для типа,
        // отличного от LIST, — у списка значения нет.
        String form = mockMvc.perform(get("/rpg/systems/new").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(form).contains("manual-input-block");
        assertThat(form).contains("data-attr-manual-input");
        assertThat(form).contains("row.querySelectorAll('.manual-input-block')");
        // У числа флаг есть всегда, у текста — только у выбираемого подтипа.
        assertThat(form).contains("type.value === 'NUMERIC'");
        assertThat(form).contains("type.value === 'TEXT' && selectable");

        // Анкета игры: то же правило.
        Game game = createGameFor(master, "Флаги анкеты", GameSystem.CUSTOM, GameVisibility.OPEN);
        String attrs = mockMvc.perform(get("/rpg/games/" + game.getId() + "/attributes").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(attrs).contains("id=\"attr-manual-block\"");
        assertThat(attrs).contains("manual.classList.toggle('d-none', type.value !== 'NUMERIC')");
    }

    @Test
    void listParameterNeverKeepsManualInputFlag() throws Exception {
        User master = createUser("auto_rpg_master_manual2", UserRole.RESIDENT);
        MockHttpSession session = login("auto_rpg_master_manual2");

        Game game = createGameFor(master, "Список без флага", GameSystem.CUSTOM, GameVisibility.OPEN);

        // Даже если флаг прислать для списка, сервер его снимает.
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/attributes").session(session).with(csrf())
                        .param("name", "Снаряжение")
                        .param("attributeType", "LIST")
                        .param("listKind", "SIMPLE")
                        .param("manualInputAllowed", "true"))
                .andExpect(status().is3xxRedirection());

        CustomAttribute attr = customAttributeRepository.findByGameIdOrderByPositionAsc(game.getId()).get(0);
        assertThat(attr.isManualInputAllowed()).isFalse();
    }

    @Test
    void moneyOnViewPageUpdatesWithoutReload() throws Exception {
        User master = createUser("auto_rpg_master_noreload", UserRole.RESIDENT);
        createUser("auto_rpg_player_noreload", UserRole.RESIDENT);
        Game game = createGame(master, "Монеты без перезагрузки", "Правка кнопками.",
                GameSystem.DND, GameVisibility.OPEN, GameStatus.RECRUITING);
        Long playerId = applyFor("auto_rpg_player_noreload", game, "Кошелёк");
        acceptFor(game, playerId);
        Long sheetId = sheetIdFor(game, playerId);

        String page = sheetPage(game, sheetId, "auto_rpg_player_noreload");
        // Формы денег помечены: JS перехватывает отправку и обновляет значения
        // без перезагрузки страницы.
        assertThat(page).contains("data-money-change");
        assertThat(page).contains("data-money-convert-form");
        assertThat(page).contains("data-money-value");
        // CSRF-токен берётся из самой формы: подстановка /*[[${_csrf.token}]]*/
        // в скрипт ломает его разбор, и страница теряет весь JS.
        assertThat(page).contains("input[name=\"_csrf\"]");
        assertThat(page).contains("X-CSRF-TOKEN");
        assertThat(page).doesNotContain("/*[[");

        // Значение разряда выводится в элемент с data-money-value, а не в текст
        // узла: иначе обновление без перезагрузки его бы затерело.
        assertThat(page).contains("data-money-value=\"gold\"");
        assertThat(page).contains("data-money-value=\"silver\"");
        assertThat(page).contains("data-money-value=\"copper\"");
    }

    @Test
    void librarySystemTextWithOptionsAndManualInputKeepsBoth() throws Exception {
        User master = createUser("auto_rpg_master_libmanual", UserRole.RESIDENT);
        MockHttpSession session = login("auto_rpg_master_libmanual");

        // Публикуем систему: текстовый параметр со списком значений и флагом
        // ручного ввода.
        mockMvc.perform(post("/rpg/systems/new").session(session).with(csrf())
                        .param("name", "Система с ручным вводом")
                        .param("published", "true")
                        .param("templateAttributes[0].name", "Стиль")
                        .param("templateAttributes[0].type", "TEXT")
                        .param("templateAttributes[0].optionsText", "Огонь\nВода")
                        .param("templateAttributes[0].manualInputAllowed", "true")
                        .param("templateAttributes[1].name", "Настроение")
                        .param("templateAttributes[1].type", "TEXT")
                        .param("templateAttributes[1].defaultText", "Бодрое")
                        .param("templateAttributes[1].manualInputAllowed", "true"))
                .andExpect(status().is3xxRedirection());

        Long systemId = definitionRepository.findAll().stream()
                .filter(sys -> "Система с ручным вводом".equals(sys.getName()))
                .map(GameSystemDefinition::getId).findFirst().orElseThrow();

        // Ограничения дошли до игры.
        mockMvc.perform(post("/rpg/games").session(session).with(csrf())
                        .param("title", "Игра под ручной ввод")
                        .param("description", "Проверка переноса ограничений.")
                        .param("system", "CUSTOM")
                        .param("customSystemId", String.valueOf(systemId))
                        .param("status", "RECRUITING")
                        .param("visibility", "OPEN"))
                .andExpect(status().is3xxRedirection());

        Long gameId = gameIdByTitle("Игра под ручной ввод");
        List<CustomAttribute> attrs = customAttributeRepository.findByGameIdOrderByPositionAsc(gameId);
        CustomAttribute style = attrs.stream().filter(a -> "Стиль".equals(a.getName())).findFirst().orElseThrow();
        assertThat(style.getOptions()).containsExactly("Огонь", "Вода");
        assertThat(style.isManualInputAllowed()).isTrue();

        mockMvc.perform(post("/rpg/games/" + gameId + "/npc").session(session).with(csrf())
                        .param("name", "Образец"))
                .andExpect(status().is3xxRedirection());
        Long sheetId = sheetRepository.findByGameIdOrderByCreatedAtDesc(gameId).get(0).getId();

        String form = mockMvc.perform(get("/rpg/games/" + gameId + "/characters/" + sheetId + "/edit")
                        .session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        // Список вариантов плюс пункт «Другое» с полем ручного ввода.
        String styleBlock = blockOf(form, "custom:" + style.getId());
        assertThat(styleBlock).contains("data-opt-select");
        assertThat(styleBlock).contains("__other__");
        assertThat(styleBlock).contains("data-opt-manual");

        // Значение по умолчанию подставляется в текстовое поле.
        CustomAttribute mood = attrs.stream().filter(a -> "Настроение".equals(a.getName())).findFirst().orElseThrow();
        String moodBlock = blockOf(form, "custom:" + mood.getId());
        assertThat(moodBlock).contains("value=\"Бодрое\"");
    }

    @Test
    void systemPreviewFillsFormWithDefaultValues() throws Exception {
        // Пробный лист биндит поля по sheetForm: без переноса значений по
        // умолчанию в форму стрелки числового поля считали бы пустое значение.
        User master = createUser("auto_rpg_master_preview", UserRole.RESIDENT);
        MockHttpSession session = login("auto_rpg_master_preview");
        GameSystemDefinition system = new GameSystemDefinition();
        system.setName("Система с дефолтами");
        system.setDescription("Пробный лист");
        system.setAuthorId(master.getId());
        system.setPublic(true);
        List<SystemAttributeDto> template = new ArrayList<>();
        SystemAttributeDto power = attr("Сила", AttributeType.NUMERIC, 0, null);
        power.setDefaultValue(13);
        power.setMinValue(10);
        power.setMaxValue(100);
        power.setStepValue(3);
        template.add(power);
        SystemAttributeDto mood = attr("Настроение", AttributeType.TEXT, 10, null);
        mood.setDefaultText("Бодрое");
        mood.setOptions(List.of("Бодрое", "Усталое"));
        mood.setManualInputAllowed(true);
        template.add(mood);
        system.setTemplateAttributeList(template);
        Long systemId = definitionRepository.save(system).getId();

        String preview = mockMvc.perform(get("/rpg/systems/" + systemId + "/character").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        // Поле формы заполнено значением по умолчанию, а не пустое.
        assertThat(preview).contains("name=\"attributes[0].numericValue\" value=\"13\"");
        assertThat(preview).contains("data-num-min=\"10\"");
        assertThat(preview).contains("data-spin-step=\"3\"");
        assertThat(preview).contains("data-num-view=\"custom-preview:0\">13<");
        assertThat(preview).contains("Бодрое");
        // Пункт «Другое» — ручной ввод со списком вариантов.
        assertThat(preview).contains("Другое");
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
    void dndSheetShowsMoneyInsideInventorySection() throws Exception {
        User master = createUser("auto_rpg_master_moneyview", UserRole.RESIDENT);
        createUser("auto_rpg_player_moneyview", UserRole.RESIDENT);
        Game game = createGame(master, "Деньги и инвентарь", "Один раздел листа.",
                GameSystem.DND, GameVisibility.OPEN, GameStatus.RECRUITING);
        Long playerId = applyFor("auto_rpg_player_moneyview", game, "Торговец");
        acceptFor(game, playerId);
        Long sheetId = sheetIdFor(game, playerId);

        String page = sheetPage(game, sheetId, "auto_rpg_player_moneyview");
        // Раздел один: инвентарь, внутри которого деньги и предметы.
        assertThat(page).contains("data-block=\"inventory\"");
        int inventoryBlock = page.indexOf("data-block=\"inventory\"");
        int moneyBlock = page.indexOf("data-block=\"money\"");
        assertThat(moneyBlock).isGreaterThan(inventoryBlock);
        // Раздел инвентаря на листе один.
        assertThat(countOccurrences(page, "data-block=\"inventory\"")).isEqualTo(1);
        assertThat(page).contains("Золотые").contains("Серебряные").contains("Медные");
        assertThat(page).contains("Предметы");
        assertThat(page).doesNotContain("money/set");
        // Денежные параметры и инвентарь не показываются в общем списке параметров,
        // разделы листа идут по порядку: параметры, инвентарь, ячейки.
        int paramsBlock = page.indexOf("bi-sliders");
        String paramsRegion = page.substring(paramsBlock, inventoryBlock);
        assertThat(paramsBlock).isLessThan(inventoryBlock);
        assertThat(paramsRegion).doesNotContain("Золотые");
        assertThat(paramsRegion).doesNotContain("Медные");
        assertThat(paramsRegion).doesNotContain("inventory-add");
    }

    @Test
    void dndSheetMoneyHasNoManualInputOnViewPage() throws Exception {
        User master = createUser("auto_rpg_master_nomoneyinput", UserRole.RESIDENT);
        createUser("auto_rpg_player_nomoneyinput", UserRole.RESIDENT);
        Game game = createGame(master, "Только кнопки денег", "Ручного ввода в просмотре нет.",
                GameSystem.DND, GameVisibility.OPEN, GameStatus.RECRUITING);
        Long playerId = applyFor("auto_rpg_player_nomoneyinput", game, "Копилка");
        acceptFor(game, playerId);
        Long sheetId = sheetIdFor(game, playerId);
        setNumeric(sheetId, "money:gold", 3);

        String page = sheetPage(game, sheetId, "auto_rpg_player_nomoneyinput");
        int moneyBlock = page.indexOf("data-block=\"money\"");
        String moneyRegion = page.substring(moneyBlock, page.indexOf("</main>"));
        // Значение видно, кнопки ± и обмен есть, поля ввода нет.
        assertThat(moneyRegion).doesNotContain("type=\"number\"");
        assertThat(moneyRegion).doesNotContain("money/set");
        assertThat(moneyRegion).contains("money/change");
        assertThat(moneyRegion).contains("money/convert");
        assertThat(moneyRegion).contains("Золотые");
        assertThat(moneyRegion).contains("3");
        // Обмен вынесен между строк с числом монет: после своей строки и до следующей.
        assertThat(moneyRegion).contains("data-money-convert-row");
        assertThat(moneyRegion.indexOf("data-money-row=\"gold\""))
                .isLessThan(moneyRegion.indexOf("data-money-convert-row=\"gold\""));
        assertThat(moneyRegion.indexOf("data-money-convert-row=\"gold\""))
                .isLessThan(moneyRegion.indexOf("data-money-row=\"silver\""));
        assertThat(moneyRegion.indexOf("data-money-convert-row=\"silver\""))
                .isLessThan(moneyRegion.indexOf("data-money-row=\"copper\""));
        // У младшего разряда обмена нет.
        assertThat(moneyRegion).doesNotContain("data-money-convert-row=\"copper\"");
    }

    @Test
    void moneyManualInputIsNotAvailableOnViewPage() throws Exception {
        User master = createUser("auto_rpg_master_moneyset", UserRole.RESIDENT);
        createUser("auto_rpg_player_moneyset", UserRole.RESIDENT);
        Game game = createGame(master, "Ввод денег", "Ручной ввод только в форме листа.",
                GameSystem.DND, GameVisibility.OPEN, GameStatus.RECRUITING);
        Long playerId = applyFor("auto_rpg_player_moneyset", game, "Копилка");
        acceptFor(game, playerId);
        Long sheetId = sheetIdFor(game, playerId);
        MockHttpSession session = login("auto_rpg_player_moneyset");

        // В форме листа сумма задаётся сразу по всем видам монет.
        String form = mockMvc.perform(get("/rpg/games/" + game.getId() + "/characters/" + sheetId + "/edit")
                        .session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        int goldIdx = attributeIndex(sheetId, "money:gold");
        int silverIdx = attributeIndex(sheetId, "money:silver");
        int copperIdx = attributeIndex(sheetId, "money:copper");
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/characters/" + sheetId + "/edit")
                        .session(session).with(csrf())
                        .param("name", "Копилка")
                        .param("attributes[" + goldIdx + "].key", "money:gold")
                        .param("attributes[" + goldIdx + "].numericValue", "12")
                        .param("attributes[" + silverIdx + "].key", "money:silver")
                        .param("attributes[" + silverIdx + "].numericValue", "34")
                        .param("attributes[" + copperIdx + "].key", "money:copper")
                        .param("attributes[" + copperIdx + "].numericValue", "56"))
                .andExpect(status().is3xxRedirection());

        assertThat(numericOf(sheetId, "money:gold")).isEqualTo(12);
        assertThat(numericOf(sheetId, "money:silver")).isEqualTo(34);
        assertThat(numericOf(sheetId, "money:copper")).isEqualTo(56);
        // На странице просмотра полей ручного ввода нет.
        assertThat(sheetPage(game, sheetId, "auto_rpg_player_moneyset")).doesNotContain("money/set");
    }

    @Test
    void moneyButtonsAddOneCoinPerPressAndNeverGoNegative() throws Exception {
        User master = createUser("auto_rpg_master_moneybtn", UserRole.RESIDENT);
        createUser("auto_rpg_player_moneybtn", UserRole.RESIDENT);
        Game game = createGame(master, "Кнопки денег", "Плюс и минус.",
                GameSystem.DND, GameVisibility.OPEN, GameStatus.RECRUITING);
        Long playerId = applyFor("auto_rpg_player_moneybtn", game, "Бродяга");
        acceptFor(game, playerId);
        Long sheetId = sheetIdFor(game, playerId);
        MockHttpSession session = login("auto_rpg_player_moneybtn");

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/characters/" + sheetId + "/money/change")
                        .session(session).with(csrf())
                        .param("kind", "gold").param("delta", "1"))
                .andExpect(status().is3xxRedirection());
        assertThat(numericOf(sheetId, "money:gold")).isEqualTo(1);

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/characters/" + sheetId + "/money/change")
                        .session(session).with(csrf())
                        .param("kind", "gold").param("delta", "-1"))
                .andExpect(status().is3xxRedirection());
        assertThat(numericOf(sheetId, "money:gold")).isEqualTo(0);

        // Минус у нуля не уводит сумму в минус.
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/characters/" + sheetId + "/money/change")
                        .session(session).with(csrf())
                        .param("kind", "gold").param("delta", "-1"))
                .andExpect(status().is3xxRedirection());
        assertThat(numericOf(sheetId, "money:gold")).isEqualTo(0);
    }

    @Test
    void moneyConvertExchangesOneCoinForTenPerPress() throws Exception {
        User master = createUser("auto_rpg_master_moneycnv", UserRole.RESIDENT);
        createUser("auto_rpg_player_moneycnv", UserRole.RESIDENT);
        Game game = createGame(master, "Обмен монет", "Курс 1:10.",
                GameSystem.DND, GameVisibility.OPEN, GameStatus.RECRUITING);
        Long playerId = applyFor("auto_rpg_player_moneycnv", game, "Меняла");
        acceptFor(game, playerId);
        Long sheetId = sheetIdFor(game, playerId);
        MockHttpSession session = login("auto_rpg_player_moneycnv");
        String change = "/rpg/games/" + game.getId() + "/characters/" + sheetId + "/money/convert";

        // 10 серебряных → 1 золотая.
        setNumeric(sheetId, "money:silver", 10);
        mockMvc.perform(post(change).session(session).with(csrf())
                        .param("kind", "gold").param("direction", "down"))
                .andExpect(status().is3xxRedirection());
        assertThat(numericOf(sheetId, "money:gold")).isEqualTo(1);
        assertThat(numericOf(sheetId, "money:silver")).isEqualTo(0);

        // 1 золотая → 10 серебряных.
        mockMvc.perform(post(change).session(session).with(csrf())
                        .param("kind", "gold").param("direction", "up"))
                .andExpect(status().is3xxRedirection());
        assertThat(numericOf(sheetId, "money:gold")).isEqualTo(0);
        assertThat(numericOf(sheetId, "money:silver")).isEqualTo(10);

        // Некратное 10 вверх не обменивается: остаётся в младшем разряде.
        setNumeric(sheetId, "money:silver", 15);
        mockMvc.perform(post(change).session(session).with(csrf())
                        .param("kind", "gold").param("direction", "down"))
                .andExpect(status().is3xxRedirection());
        assertThat(numericOf(sheetId, "money:gold")).isEqualTo(1);
        assertThat(numericOf(sheetId, "money:silver")).isEqualTo(5);

        // Медь в серебро: 10 медных → 1 серебряная.
        setNumeric(sheetId, "money:copper", 10);
        mockMvc.perform(post(change).session(session).with(csrf())
                        .param("kind", "silver").param("direction", "down"))
                .andExpect(status().is3xxRedirection());
        assertThat(numericOf(sheetId, "money:copper")).isEqualTo(0);
        assertThat(numericOf(sheetId, "money:silver")).isEqualTo(6);
    }

    @Test
    void dndEditFormShowsMoneyInsideInventorySection() throws Exception {
        User master = createUser("auto_rpg_master_moneyform", UserRole.RESIDENT);
        createUser("auto_rpg_player_moneyform", UserRole.RESIDENT);
        Game game = createGame(master, "Разделы формы листа", "Инвентарь и деньги в одном разделе.",
                GameSystem.DND, GameVisibility.OPEN, GameStatus.RECRUITING);
        Long playerId = applyFor("auto_rpg_player_moneyform", game, "Скупщик");
        acceptFor(game, playerId);
        Long sheetId = sheetIdFor(game, playerId);

        String page = mockMvc.perform(get("/rpg/games/" + game.getId() + "/characters/" + sheetId + "/edit")
                        .session(login("auto_rpg_player_moneyform")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        // Раздел один: инвентарь с деньгами и предметами, рядом блок «Ячейки».
        int inventoryBlock = page.indexOf("data-block=\"inventory\"");
        int moneyBlock = page.indexOf("data-block=\"money\"");
        int slotsBlock = page.indexOf("id=\"spell-slots-block\"");
        assertThat(inventoryBlock).isGreaterThan(0);
        assertThat(moneyBlock).isGreaterThan(inventoryBlock);
        assertThat(moneyBlock).isLessThan(slotsBlock);
        assertThat(page).contains("Инвентарь").contains("Деньги").contains("Предметы");
        // В общем списке параметров их нет.
        String paramsRegion = page.substring(page.indexOf("Параметры"), inventoryBlock);
        assertThat(paramsRegion).doesNotContain("Золотые");
        assertThat(paramsRegion).doesNotContain("data-simple-input=\"inventory\"");
        // Ручной ввод денег в форме есть — это страница редактирования.
        assertThat(page).contains("data-money-input=\"money:gold\"");
        assertThat(page).contains("min=\"0\"");
    }

    @Test
    void dndEditFormHasMoneyConversionControls() throws Exception {
        User master = createUser("auto_rpg_master_moneyconv", UserRole.RESIDENT);
        createUser("auto_rpg_player_moneyconv", UserRole.RESIDENT);
        Game game = createGame(master, "Обмен монет в форме", "Курс 1:10.",
                GameSystem.DND, GameVisibility.OPEN, GameStatus.RECRUITING);
        Long playerId = applyFor("auto_rpg_player_moneyconv", game, "Меняла");
        acceptFor(game, playerId);
        Long sheetId = sheetIdFor(game, playerId);
        setNumeric(sheetId, "money:gold", 1);
        setNumeric(sheetId, "money:silver", 15);

        String page = mockMvc.perform(get("/rpg/games/" + game.getId() + "/characters/" + sheetId + "/edit")
                        .session(login("auto_rpg_player_moneyconv")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        int moneyBlock = page.indexOf("data-block=\"money\"");
        String moneyRegion = page.substring(moneyBlock, page.indexOf("id=\"spell-slots-block\""));
        // Для каждого разряда с младшим — обмен в обе стороны, у меди обмена нет.
        assertThat(moneyRegion).contains("data-money-convert=\"up\"");
        assertThat(moneyRegion).contains("data-money-convert=\"down\"");
        assertThat(moneyRegion).doesNotContain("data-money-convert=\"up\" th:attr");
        assertThat(moneyRegion).contains("10 см");
        assertThat(moneyRegion).contains("1 зм");
        // Обмен стоит между строк с числом монет.
        assertThat(moneyRegion.indexOf("data-money-row=\"money:gold\""))
                .isLessThan(moneyRegion.indexOf("data-money-convert-row=\"money:gold\""));
        assertThat(moneyRegion.indexOf("data-money-convert-row=\"money:gold\""))
                .isLessThan(moneyRegion.indexOf("data-money-row=\"money:silver\""));
        assertThat(moneyRegion).doesNotContain("data-money-convert-row=\"money:copper\"");
    }

    @Test
    void dndNumbersInEditFormHaveNoManualInputOutsideMoney() throws Exception {
        User master = createUser("auto_rpg_master_arrows", UserRole.RESIDENT);
        createUser("auto_rpg_player_arrows", UserRole.RESIDENT);
        Game game = createGame(master, "Только стрелки", "Числа D&D правятся стрелками.",
                GameSystem.DND, GameVisibility.OPEN, GameStatus.RECRUITING);
        Long playerId = applyFor("auto_rpg_player_arrows", game, "Воин");
        acceptFor(game, playerId);
        Long sheetId = sheetIdFor(game, playerId);

        String page = mockMvc.perform(get("/rpg/games/" + game.getId() + "/characters/" + sheetId + "/edit")
                        .session(login("auto_rpg_player_arrows")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        int inventoryBlock = page.indexOf("data-block=\"inventory\"");
        String paramsRegion = page.substring(page.indexOf("Параметры"), inventoryBlock);
        // Ни у одного числового параметра D&D нет ручного ввода.
        assertThat(paramsRegion).doesNotContain("num-manual");
        assertThat(paramsRegion).doesNotContain("Силу");
        // Стрелки у числовых параметров на месте.
        assertThat(paramsRegion).contains("btn-spin");
        assertThat(paramsRegion).contains("data-num-of=\"ability:strength\"");
        // Монеты — единственное поле с ручным вводом.
        String moneyRegion = page.substring(inventoryBlock, page.indexOf("id=\"spell-slots-block\""));
        assertThat(moneyRegion).contains("data-money-input=\"money:gold\"");
        assertThat(moneyRegion).contains("data-money-step");
    }

    @Test
    void sheetFormSavesBlocksRenderedOutsideParameterList() throws Exception {
        // Ячейки, деньги и инвентарь вынесены из общего списка параметров, поэтому
        // каждый блок обязан отдавать ключ параметра сам: сервер сопоставляет
        // значения формы по ключу и запись без ключа молча отбрасывает.
        User master = createUser("auto_rpg_master_blockkeys", UserRole.RESIDENT);
        createUser("auto_rpg_player_blockkeys", UserRole.RESIDENT);
        Game game = createGame(master, "Ключи блоков", "Сохранение значений блоков.",
                GameSystem.DND, GameVisibility.OPEN, GameStatus.RECRUITING);
        Long playerId = applyFor("auto_rpg_player_blockkeys", game, "Хранитель");
        acceptFor(game, playerId);
        Long sheetId = sheetIdFor(game, playerId);
        setNumeric(sheetId, "spell_slot_max:1", 4);
        MockHttpSession session = login("auto_rpg_player_blockkeys");

        String page = mockMvc.perform(get("/rpg/games/" + game.getId() + "/characters/" + sheetId + "/edit")
                        .session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(page).contains("value=\"money:gold\"");
        assertThat(page).contains("value=\"spell_slot_current:1\"");
        assertThat(page).contains("value=\"spell_slot_max:1\"");
        assertThat(page).contains("value=\"inventory\"");

        // Отправляем форму ровно так, как браузер: все её поля, ключи входят
        // отдельными hidden-полями и приезжают вместе со значениями.
        MultiValueMap<String, String> form = formFields(page);
        assertThat(form).containsKeys(
                "attributes[" + attributeIndex(sheetId, "money:gold") + "].key",
                "attributes[" + attributeIndex(sheetId, "spell_slot_current:1") + "].key",
                "attributes[" + attributeIndex(sheetId, "inventory") + "].key");
        setField(form, attributeIndex(sheetId, "money:gold"), "numericValue", "9");
        setField(form, attributeIndex(sheetId, "spell_slot_current:1"), "numericValue", "3");

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/characters/" + sheetId + "/edit")
                        .session(session).with(csrf())
                        .params(form))
                .andExpect(status().is3xxRedirection());

        assertThat(numericOf(sheetId, "money:gold")).isEqualTo(9);
        assertThat(numericOf(sheetId, "spell_slot_current:1")).isEqualTo(3);
    }

    @Test
    void sheetFormValidatesValuesBeforeSubmitAndKeepsSaveEnabled() throws Exception {
        // Проверка на стороне клиента: значения проверяются при вводе, кнопка
        // сохранения помечается атрибутом проверки и блокируется при ошибке.
        User master = createUser("auto_rpg_master_valid", UserRole.RESIDENT);
        Game game = createGameFor(master, "Проверка в форме", GameSystem.CUSTOM, GameVisibility.OPEN);
        MockHttpSession session = login("auto_rpg_master_valid");
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/attributes").session(session).with(csrf())
                        .param("name", "Мана")
                        .param("attributeType", "NUMERIC")
                        .param("minValue", "0").param("maxValue", "20"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/npc").session(session).with(csrf())
                        .param("name", "Образец"))
                .andExpect(status().is3xxRedirection());
        Long sheetId = sheetRepository.findByGameIdOrderByCreatedAtDesc(game.getId()).get(0).getId();
        // id параметра в анкете не всегда 1: в базе их накапливается за тесты.
        String attrKey = CustomCharacterSystem.KEY_PREFIX
                + customAttributeRepository.findByGameIdOrderByPositionAsc(game.getId()).get(0).getId();

        String form = mockMvc.perform(get("/rpg/games/" + game.getId() + "/characters/" + sheetId + "/edit")
                        .session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        // Точки, за которые отвечает клиентская проверка.
        assertThat(form).contains("data-validate-range");
        assertThat(form).contains("data-validate-options");
        assertThat(form).contains("id=\"sheet-submit\"");
        assertThat(form).contains("validateSheet()");
        assertThat(form).contains("data-validation-error");
        // Серверная проверка при этом остаётся: клиентская её не заменяет.
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/characters/" + sheetId + "/edit")
                        .session(session).with(csrf())
                        .param("name", "Образец")
                        .param("attributes[0].key", attrKey)
                        .param("attributes[0].numericValue", "50"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void textFieldWithWhitelistOffersOtherAndKeepsDefaultValue() throws Exception {
        User master = createUser("auto_rpg_master_wl", UserRole.RESIDENT);
        Game game = createGameFor(master, "Белый список", GameSystem.CUSTOM, GameVisibility.OPEN);
        MockHttpSession session = login("auto_rpg_master_wl");
        String attributes = "/rpg/games/" + game.getId() + "/attributes";

        mockMvc.perform(post(attributes).session(session).with(csrf())
                        .param("name", "Стиль")
                        .param("attributeType", "TEXT")
                        .param("optionsText", "Бодрое\nУсталое")
                        .param("defaultText", "Бодрое")
                        .param("manualInputAllowed", "true"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/npc").session(session).with(csrf())
                        .param("name", "Образец"))
                .andExpect(status().is3xxRedirection());
        Long sheetId = sheetRepository.findByGameIdOrderByCreatedAtDesc(game.getId()).get(0).getId();

        String form = mockMvc.perform(get("/rpg/games/" + game.getId() + "/characters/" + sheetId + "/edit")
                        .session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        // Пункт «Другое» есть: поле с белым списком заполняется и руками.
        assertThat(form).contains("__other__");
        // Значение по умолчанию дошло до скрытого поля формы — именно из него
        // скрипт выбирает пункт списка (в разметке отмеченных пунктов нет).
        String row = form.substring(form.indexOf("opt-picker"), form.indexOf("opt-picker") + 2000);
        assertThat(row).contains("value=\"Бодрое\"");
    }

    @Test
    void systemFormHasTextSubtypeWithFreeAndSelectableModes() throws Exception {
        User master = createUser("auto_rpg_master_subtype", UserRole.RESIDENT);
        MockHttpSession session = login("auto_rpg_master_subtype");

        String form = mockMvc.perform(get("/rpg/systems/new").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(form).contains("data-attr-text-mode");
        assertThat(form).contains("data-attr-selectable");
        assertThat(form).contains("value=\"FREE\"");
        assertThat(form).contains("value=\"SELECT\"");
        assertThat(form).contains("Свободный");
        assertThat(form).contains("Выбираемый");
        // Заголовок «Вид» и маркер предупреждения о значении вне списка.
        assertThat(form).contains("Вид</label>");
        assertThat(form).contains("data-default-warning");
        assertThat(form).contains("syncDefaultWarning");

        // Анкета игры: тот же выбор подтипа.
        Game game = createGameFor(master, "Подтип текста", GameSystem.CUSTOM, GameVisibility.OPEN);
        String attrs = mockMvc.perform(get("/rpg/games/" + game.getId() + "/attributes").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(attrs).contains("id=\"attr-text-mode\"");
        assertThat(attrs).contains("Свободный");
        assertThat(attrs).contains("Выбираемый");
        assertThat(attrs).contains("id=\"attr-default-warning\"");
    }

    @Test
    void systemKeepsTextKindAndAddsDefaultToOptions() throws Exception {
        User master = createUser("auto_rpg_master_kind", UserRole.RESIDENT);
        MockHttpSession session = login("auto_rpg_master_kind");

        mockMvc.perform(post("/rpg/systems/new").session(session).with(csrf())
                        .param("name", "Виды")
                        .param("description", "Сохранение вида")
                        .param("templateAttributes[0].name", "Стиль")
                        .param("templateAttributes[0].type", "TEXT")
                        .param("templateAttributes[0].textMode", "SELECT")
                        .param("templateAttributes[0].optionsText", "Север\nЮг")
                        .param("templateAttributes[0].defaultText", "Бодрое")
                        .param("templateAttributes[0].manualInputAllowed", "true"))
                .andExpect(status().is3xxRedirection());

        Long systemId = definitionRepository.findByNameIgnoreCase("Виды").orElseThrow().getId();
        String json = definitionRepository.findById(systemId).orElseThrow().getTemplateAttributes();
        assertThat(json).contains("\"textMode\":\"SELECT\"");
        // Значение по умолчанию, которого не было в списке, добавляется в него.
        assertThat(json).contains("Бодрое");

        // При правке системы выбранный вид сохраняется, а не пересчитывается.
        String edit = mockMvc.perform(get("/rpg/systems/" + systemId + "/edit").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(edit).contains("value=\"SELECT\" selected=\"selected\"");
        assertThat(edit).contains("Север\nЮг\nБодрое");
    }

    @Test
    void gameAttributeAddsDefaultTextToOptions() throws Exception {
        User master = createUser("auto_rpg_master_kind2", UserRole.RESIDENT);
        Game game = createGameFor(master, "Вид текста", GameSystem.CUSTOM, GameVisibility.OPEN);
        MockHttpSession session = login("auto_rpg_master_kind2");

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/attributes").session(session).with(csrf())
                        .param("name", "Стиль")
                        .param("attributeType", "TEXT")
                        .param("textMode", "SELECT")
                        .param("optionsText", "Север\nЮг")
                        .param("defaultText", "Бодрое"))
                .andExpect(status().is3xxRedirection());

        CustomAttribute attr = customAttributeRepository
                .findByGameIdOrderByPositionAsc(game.getId()).get(0);
        assertThat(attr.getOptions()).containsExactly("Север", "Юг", "Бодрое");
        assertThat(attr.getDefaultText()).isEqualTo("Бодрое");
    }

    @Test
    void sheetPagesHaveNoDuplicatedNavigationLinks() throws Exception {
        // На странице системы и пробном листе ссылка «назад» должна быть одна:
        // повтор в шапке и внизу страницы выглядел как две разные кнопки.
        User master = createUser("auto_rpg_master_nav", UserRole.RESIDENT);
        MockHttpSession session = login("auto_rpg_master_nav");

        Long systemId = createSystemWithTextAttribute(master);
        String view = mockMvc.perform(get("/rpg/systems/" + systemId).session(session))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(count(view, "К библиотеке систем")).isEqualTo(1);
        assertThat(count(view, "Назад к библиотеке")).isZero();

        String preview = mockMvc.perform(get("/rpg/systems/" + systemId + "/character")
                        .session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(count(preview, "К системе")).isEqualTo(1);
    }

    @Test
    void simpleListInputHasNoPlaceholder() throws Exception {
        // Пустая строка ввода простого списка не подсказывает «Название предмета»:
        // рядом кнопка «+», смысл действия и так понятен.
        User master = createUser("auto_rpg_master_noph", UserRole.RESIDENT);
        MockHttpSession session = login("auto_rpg_master_noph");
        Long systemId = createSystemWithTextAttribute(master);

        String preview = mockMvc.perform(get("/rpg/systems/" + systemId + "/character").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(preview).contains("data-simple-input");
        assertThat(preview).doesNotContain("Название предмета");
    }

    /** Система с простым списком — на её страницах и проверяются ссылки и поле ввода. */
    private Long createSystemWithTextAttribute(User master) throws Exception {
        MockHttpSession session = login(master.getUsername());
        mockMvc.perform(post("/rpg/systems/new").session(session).with(csrf())
                        .param("name", "Списки " + master.getUsername())
                        .param("description", "Проверка страницы системы")
                        .param("templateAttributes[0].name", "Снаряжение")
                        .param("templateAttributes[0].type", "LIST")
                        .param("templateAttributes[0].listKind", "SIMPLE"))
                .andExpect(status().is3xxRedirection());
        return definitionRepository.findByNameIgnoreCase("Списки " + master.getUsername())
                .orElseThrow().getId();
    }

    @Test
    void moneyEndpointsIgnoreRequestWithoutKind() throws Exception {
        // Запрос без параметров (например, от клиента без JS или с неполной
        // формой) не должен давать 500: операция просто не выполняется.
        User master = createUser("auto_rpg_master_nokind", UserRole.RESIDENT);
        createUser("auto_rpg_player_nokind", UserRole.RESIDENT);
        Game game = createGame(master, "Пустой запрос денег", "Проверка устойчивости.",
                GameSystem.DND, GameVisibility.OPEN, GameStatus.RECRUITING);
        Long playerId = applyFor("auto_rpg_player_nokind", game, "Проситель");
        acceptFor(game, playerId);
        Long sheetId = sheetIdFor(game, playerId);
        setNumeric(sheetId, "money:gold", 3);
        MockHttpSession session = login("auto_rpg_player_nokind");
        String change = "/rpg/games/" + game.getId() + "/characters/" + sheetId + "/money/change";
        String convert = "/rpg/games/" + game.getId() + "/characters/" + sheetId + "/money/convert";

        mockMvc.perform(post(change).session(session).with(csrf()))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post(change).session(session).with(csrf()).param("delta", "1"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post(convert).session(session).with(csrf()).param("direction", "up"))
                .andExpect(status().is3xxRedirection());

        assertThat(numericOf(sheetId, "money:gold")).isEqualTo(3);
    }

    @Test
    void gameAnnotationAcceptsTextDefaultValue() throws Exception {
        // Анкета игры: у текстового параметра есть значение по умолчанию, и оно
        // попадает в поле листа.
        User master = createUser("auto_rpg_master_textdef", UserRole.RESIDENT);
        Game game = createGameFor(master, "Дефолт анкеты", GameSystem.CUSTOM, GameVisibility.OPEN);
        MockHttpSession session = login("auto_rpg_master_textdef");
        String attributes = "/rpg/games/" + game.getId() + "/attributes";

        String form = mockMvc.perform(get(attributes).session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(form).contains("name=\"defaultText\"");

        // Свободный текст: значение по умолчанию задаётся без списка вариантов.
        mockMvc.perform(post(attributes).session(session).with(csrf())
                        .param("name", "Настроение")
                        .param("attributeType", "TEXT")
                        .param("defaultText", "Бодрое")
                        .param("manualInputAllowed", "true"))
                .andExpect(status().is3xxRedirection());

        // Значение по умолчанию не из списка: автору показывается предупреждение,
        // а при сохранении само значение становится вариантом.
        mockMvc.perform(post(attributes).session(session).with(csrf())
                        .param("name", "Стиль")
                        .param("attributeType", "TEXT")
                        .param("textMode", "SELECT")
                        .param("optionsText", "Огонь\nВода")
                        .param("defaultText", "Земля")
                        .param("manualInputAllowed", "true"))
                .andExpect(status().is3xxRedirection());
        CustomAttribute styled = customAttributeRepository
                .findByGameIdOrderByPositionAsc(game.getId()).get(
                        customAttributeRepository.findByGameIdOrderByPositionAsc(game.getId()).size() - 1);
        assertThat(styled.getName()).isEqualTo("Стиль");
        assertThat(styled.getOptions()).containsExactly("Огонь", "Вода", "Земля");

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/npc").session(session).with(csrf())
                        .param("name", "Образец"))
                .andExpect(status().is3xxRedirection());
        Long sheetId = sheetRepository.findByGameIdOrderByCreatedAtDesc(game.getId()).get(0).getId();

        String sheetForm = mockMvc.perform(get("/rpg/games/" + game.getId() + "/characters/" + sheetId + "/edit")
                        .session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(sheetForm).contains("value=\"Бодрое\"");
    }

    @Test
    void numericSpinKeepsValueWhenStepLeavesRange() throws Exception {
        // Проверка правила в клиентском коде: при выходе за границу значение не
        // подгоняется под неё, а остаётся прежним (нижняя граница 10, шаг 3,
        // значение 11, шаг вниз — остаётся 11). Динамику нажатия проверяет
        // браузер, здесь — наличие правила в коде формы.
        User master = createUser("auto_rpg_master_steps", UserRole.RESIDENT);
        Game game = createGameFor(master, "Шаг за границей", GameSystem.CUSTOM, GameVisibility.OPEN);
        MockHttpSession session = login("auto_rpg_master_steps");
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/attributes").session(session).with(csrf())
                        .param("name", "Сила")
                        .param("attributeType", "NUMERIC")
                        .param("minValue", "10")
                        .param("maxValue", "100")
                        .param("stepValue", "3")
                        .param("defaultValue", "13")
                        .param("manualInputAllowed", "true"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/npc").session(session).with(csrf())
                        .param("name", "Образец"))
                .andExpect(status().is3xxRedirection());
        Long sheetId = sheetRepository.findByGameIdOrderByCreatedAtDesc(game.getId()).get(0).getId();

        String form = mockMvc.perform(get("/rpg/games/" + game.getId() + "/characters/" + sheetId + "/edit")
                        .session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(form).contains("data-num-min=\"10\"");
        assertThat(form).contains("data-spin-step=\"-3\"");
        assertThat(form).contains("if ((!isNaN(min) && next < min) || (!isNaN(max) && next > max)) {");
        // Подгонки под границу в правиле нет.
        assertThat(form).doesNotContain("next = Math.max(next, min)");
    }

    @Test
    void librarySystemRejectsBlankNameAndTextDefaultOutsideOptions() throws Exception {
        User master = createUser("auto_rpg_master_libvalid", UserRole.RESIDENT);
        MockHttpSession session = login("auto_rpg_master_libvalid");

        // Параметр без имени — ошибка, а не молчаливое пропускание строки.
        mockMvc.perform(post("/rpg/systems/new").session(session).with(csrf())
                        .param("name", "Без имени параметра")
                        .param("templateAttributes[0].type", "NUMERIC")
                        .param("templateAttributes[0].defaultValue", "3"))
                .andExpect(status().isBadRequest());

        // Значение по умолчанию числового поля вне границ.
        mockMvc.perform(post("/rpg/systems/new").session(session).with(csrf())
                        .param("name", "Дефолт вне границ")
                        .param("templateAttributes[0].name", "Мана")
                        .param("templateAttributes[0].type", "NUMERIC")
                        .param("templateAttributes[0].minValue", "1")
                        .param("templateAttributes[0].maxValue", "10")
                        .param("templateAttributes[0].defaultValue", "50"))
                .andExpect(status().isBadRequest());

        // Значение по умолчанию не из списка: система сохраняется, а значение
        // само дополняет список вариантов.
        mockMvc.perform(post("/rpg/systems/new").session(session).with(csrf())
                        .param("name", "Дефолт не из списка")
                        .param("templateAttributes[0].name", "Стиль")
                        .param("templateAttributes[0].type", "TEXT")
                        .param("templateAttributes[0].textMode", "SELECT")
                        .param("templateAttributes[0].optionsText", "Огонь\nВода")
                        .param("templateAttributes[0].defaultText", "Земля")
                        .param("templateAttributes[0].manualInputAllowed", "true"))
                .andExpect(status().is3xxRedirection());
        assertThat(definitionRepository.findByNameIgnoreCase("Дефолт не из списка").orElseThrow()
                .getTemplateAttributeList().get(0).getOptions())
                .containsExactly("Огонь", "Вода", "Земля");

        // Корректные значения принимаются.
        mockMvc.perform(post("/rpg/systems/new").session(session).with(csrf())
                        .param("name", "Корректные ограничения")
                        .param("templateAttributes[0].name", "Стиль")
                        .param("templateAttributes[0].type", "TEXT")
                        .param("templateAttributes[0].optionsText", "Огонь\nВода")
                        .param("templateAttributes[0].defaultText", "Огонь")
                        .param("templateAttributes[0].manualInputAllowed", "true"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void customTextWithOptionsAndManualInputOffersOtherAndDefault() throws Exception {
        User master = createUser("auto_rpg_master_customother", UserRole.RESIDENT);
        Game game = createGameFor(master, "Ручной ввод со списком", GameSystem.CUSTOM, GameVisibility.OPEN);
        MockHttpSession session = login("auto_rpg_master_customother");
        String attributes = "/rpg/games/" + game.getId() + "/attributes";

        // Варианты есть, ручной ввод разрешён, значение по умолчанию — из списка.
        mockMvc.perform(post(attributes).session(session).with(csrf())
                        .param("name", "Стиль")
                        .param("attributeType", "TEXT")
                        .param("optionsText", "Огонь\nВода")
                        .param("defaultText", "Огонь")
                        .param("manualInputAllowed", "true"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/npc").session(session).with(csrf())
                        .param("name", "Образец"))
                .andExpect(status().is3xxRedirection());
        Long sheetId = sheetRepository.findByGameIdOrderByCreatedAtDesc(game.getId()).get(0).getId();

        String form = mockMvc.perform(get("/rpg/games/" + game.getId() + "/characters/" + sheetId + "/edit")
                        .session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        // Список вариантов с пунктом «Другое», раскрывающим ручной ввод.
        assertThat(form).contains("opt-picker");
        assertThat(form).contains("value=\"__other__\"");
        assertThat(form).contains("Другое");
        assertThat(form).contains("data-opt-manual");
        // Значение по умолчанию подставлено в поле.
        assertThat(form).contains("value=\"Огонь\"");
    }

    @Test
    void dndMoneyCannotGoBelowZeroThroughSheetForm() throws Exception {
        User master = createUser("auto_rpg_master_moneymin", UserRole.RESIDENT);
        createUser("auto_rpg_player_moneymin", UserRole.RESIDENT);
        Game game = createGame(master, "Монеты не ниже нуля", "Отрицательное значение отклоняется.",
                GameSystem.DND, GameVisibility.OPEN, GameStatus.RECRUITING);
        Long playerId = applyFor("auto_rpg_player_moneymin", game, "Копилка");
        acceptFor(game, playerId);
        Long sheetId = sheetIdFor(game, playerId);
        setNumeric(sheetId, "money:gold", 5);
        MockHttpSession session = login("auto_rpg_player_moneymin");

        // В форме у монет задана нижняя граница.
        String page = mockMvc.perform(get("/rpg/games/" + game.getId() + "/characters/" + sheetId + "/edit")
                        .session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(page).contains("data-money-min=\"0\"");

        int goldIdx = attributeIndex(sheetId, "money:gold");
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/characters/" + sheetId + "/edit")
                        .session(session).with(csrf())
                        .param("name", "Копилка")
                        .param("attributes[" + goldIdx + "].key", "money:gold")
                        .param("attributes[" + goldIdx + "].numericValue", "-5"))
                .andExpect(status().isBadRequest());

        assertThat(numericOf(sheetId, "money:gold")).isEqualTo(5);
    }

    @Test
    void moneyBlockIsShownForDndOnly() throws Exception {
        User master = createUser("auto_rpg_master_moneyonly", UserRole.RESIDENT);
        createUser("auto_rpg_player_moneyonly", UserRole.RESIDENT);
        Game verbal = createGame(master, "Лист без денег", "Система без монет.",
                GameSystem.VERBAL, GameVisibility.OPEN, GameStatus.RECRUITING);
        Long playerId = applyFor("auto_rpg_player_moneyonly", verbal, "Рассказчик");
        acceptFor(verbal, playerId);

        String page = sheetPage(verbal, sheetIdFor(verbal, playerId), "auto_rpg_player_moneyonly");
        // Разрядов монет на листе нет (маркер строки разряда, а не блок: сам
        // блок «Инвентарь» в разметке есть всегда).
        assertThat(page).doesNotContain("data-money-row");
        assertThat(page).doesNotContain("Золотые");
        // Инвентарь при этом остаётся: он есть у любой системы.
        assertThat(page).contains("data-block=\"inventory\"");
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
    void systemLibraryIsRowListPaginatedAndSearchable() throws Exception {
        User author = createUser("auto_rpg_lib_author", UserRole.RESIDENT);
        for (int i = 1; i <= 12; i++) {
            createLibrarySystemWithTemplate(author, "Библиотека " + i);
        }

        String first = mockMvc.perform(get("/rpg/systems").param("search", "Библиотека ")
                        .session(login("auto_rpg_lib_author")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(countOccurrences(first, "data-system-row=")).isEqualTo(10);
        assertThat(first).contains("Пагинация");

        String second = mockMvc.perform(get("/rpg/systems").param("search", "Библиотека ").param("page", "1")
                        .session(login("auto_rpg_lib_author")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(countOccurrences(second, "data-system-row=")).isEqualTo(2);

        // Поиск по названию сужает выдачу.
        String found = mockMvc.perform(get("/rpg/systems").param("search", "Библиотека 7")
                        .session(login("auto_rpg_lib_author")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(countOccurrences(found, "data-system-row=")).isEqualTo(1);
        assertThat(found).contains("Библиотека 7");
    }

    @Test
    void systemLibrarySeparatesMySystemsFromPublicOnes() throws Exception {
        User author = createUser("auto_rpg_lib_mine", UserRole.RESIDENT);
        User stranger = createUser("auto_rpg_lib_stranger", UserRole.RESIDENT);
        Long mine = createLibrarySystemWithTemplate(author, "Моя система");
        Long theirs = createLibrarySystemWithTemplate(stranger, "Чужая система");

        String page = mockMvc.perform(get("/rpg/systems")
                        .session(login("auto_rpg_lib_mine")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(page).contains("Мои системы");
        assertThat(page).contains("Публичные системы");
        assertThat(page).contains("data-section=\"mine\"");
        assertThat(page).contains("data-section=\"public\"");
        String mineRow = "data-system-row=\"" + mine + "\"";
        String theirsRow = "data-system-row=\"" + theirs + "\"";
        assertThat(page).contains(mineRow);
        assertThat(page).contains(theirsRow);
        // В разделе «мои» есть только своя система, чужая — в публичных.
        int mineSection = indexOf(page, "data-section=\"mine\"");
        int publicSection = indexOf(page, "data-section=\"public\"");
        assertThat(indexOf(page, mineRow)).isGreaterThan(mineSection);
        assertThat(indexOf(page, mineRow)).isLessThan(publicSection);
        assertThat(indexOf(page, theirsRow)).isGreaterThan(publicSection);
    }

    @Test
    void gamesListIsRowListSortedByStatusThenDate() throws Exception {
        User master = createUser("auto_rpg_master_sort", UserRole.RESIDENT);
        // Порядок вставки обратен ожидаемому: проверяем сортировку, а не порядок INSERT.
        createGame(master, "Сорт A", "A.", GameSystem.VERBAL, GameVisibility.OPEN, GameStatus.CLOSED);
        createGame(master, "Сорт B", "B.", GameSystem.VERBAL, GameVisibility.OPEN, GameStatus.ACTIVE);
        createGame(master, "Сорт C", "C.", GameSystem.VERBAL, GameVisibility.OPEN, GameStatus.RECRUITING);
        createGame(master, "Сорт D", "D.", GameSystem.VERBAL, GameVisibility.OPEN, GameStatus.RECRUITING);

        String page = mockMvc.perform(get("/rpg/games").param("search", "Сорт ").session(login("auto_rpg_master_sort")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        // Набор игроков → в процессе → завершена; внутри статуса свежие выше.
        assertThat(indexOf(page, "Сорт D")).isLessThan(indexOf(page, "Сорт C"));
        assertThat(indexOf(page, "Сорт C")).isLessThan(indexOf(page, "Сорт B"));
        assertThat(indexOf(page, "Сорт B")).isLessThan(indexOf(page, "Сорт A"));
        // Список строками, а не карточками, и без бейджа системы.
        assertThat(page).contains("data-game-row=");
        assertThat(page).doesNotContain("card-title");
        assertThat(page).doesNotContain(GameSystem.VERBAL.label());
        // Статус выведен текстом, а не бейджем.
        assertThat(page).contains("Набор игроков");
        assertThat(page).contains(GameStatus.ACTIVE.label());
    }

    @Test
    void gamesListPaginatesOwnAndOtherGamesSeparately() throws Exception {
        User master = createUser("auto_rpg_master_page", UserRole.RESIDENT);
        User other = createUser("auto_rpg_other_page", UserRole.RESIDENT);
        for (int i = 1; i <= 12; i++) {
            createGame(master, "Своя страница " + i, "Описание.", GameSystem.VERBAL,
                    GameVisibility.OPEN, GameStatus.RECRUITING);
            createGame(other, "Чужая страница " + i, "Описание.", GameSystem.VERBAL,
                    GameVisibility.OPEN, GameStatus.RECRUITING);
        }
        // Раздел «Мои игры» ведущего: 12 своих игр, по 10 на страницу.
        String firstMine = mockMvc.perform(get("/rpg/games").param("search", "Своя страница ")
                        .session(login("auto_rpg_master_page")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(countOccurrences(firstMine, "data-game-row=")).isEqualTo(10);
        assertThat(firstMine).contains("Пагинация");

        String secondMine = mockMvc.perform(get("/rpg/games").param("search", "Своя страница ")
                        .param("myPage", "1").session(login("auto_rpg_master_page")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(countOccurrences(secondMine, "data-game-row=")).isEqualTo(2);

        // Общий список: те же 12 чужих игр, своя пагинация.
        String firstOthers = mockMvc.perform(get("/rpg/games").param("search", "Чужая страница ")
                        .session(login("auto_rpg_master_page")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(countOccurrences(firstOthers, "data-game-row=")).isEqualTo(10);

        String secondOthers = mockMvc.perform(get("/rpg/games").param("search", "Чужая страница ")
                        .param("page", "1").session(login("auto_rpg_master_page")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(countOccurrences(secondOthers, "data-game-row=")).isEqualTo(2);
    }

    @Test
    void gamesListSeparatesOwnGamesFromTheRest() throws Exception {
        User master = createUser("auto_rpg_master_split", UserRole.RESIDENT);
        User player = createUser("auto_rpg_player_split", UserRole.RESIDENT);
        User other = createUser("auto_rpg_other_split", UserRole.RESIDENT);

        createGame(master, "Раздел веду", "Описание.", GameSystem.VERBAL, GameVisibility.OPEN, GameStatus.ACTIVE);
        Game joined = createGame(other, "Раздел участник", "Описание.", GameSystem.VERBAL,
                GameVisibility.OPEN, GameStatus.ACTIVE);
        createGame(other, "Раздел прочие", "Описание.", GameSystem.VERBAL, GameVisibility.OPEN, GameStatus.ACTIVE);
        applyFor("auto_rpg_player_split", joined, "Участник");
        Long appId = applicationRepository.findByGameIdAndUserId(joined.getId(), player.getId()).orElseThrow().getId();
        mockMvc.perform(post("/rpg/games/" + joined.getId() + "/applications/" + appId + "/accept")
                        .session(login("auto_rpg_other_split")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        String page = mockMvc.perform(get("/rpg/games").param("search", "Раздел ")
                        .session(login("auto_rpg_player_split")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(page).contains("Мои игры");
        assertThat(page).contains("Все игры");
        assertThat(page).contains("data-section=\"mine\"");
        assertThat(page).contains("data-section=\"others\"");
        // Игра, в которой зритель участвует, — в «Моих», остальные — в общем списке.
        String mineSection = page.substring(page.indexOf("data-section=\"mine\""),
                page.indexOf("data-section=\"others\""));
        assertThat(mineSection).contains("Раздел участник");
        assertThat(mineSection).doesNotContain("Раздел прочие");

        // У ведущего его игра тоже попадает в «Мои игры».
        String masterPage = mockMvc.perform(get("/rpg/games").param("search", "Раздел ")
                        .session(login("auto_rpg_master_split")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(masterPage).contains("data-section=\"mine\"");
        String masterMine = masterPage.substring(masterPage.indexOf("data-section=\"mine\""),
                masterPage.indexOf("data-section=\"others\""));
        assertThat(masterMine).contains("Раздел веду");
        assertThat(masterMine).doesNotContain("Раздел прочие");
    }

    @Test
    void closedGamesAreLeftOutOfOwnGamesSection() throws Exception {
        User master = createUser("auto_rpg_master_closed", UserRole.RESIDENT);
        User player = createUser("auto_rpg_player_closed", UserRole.RESIDENT);
        User other = createUser("auto_rpg_other_closed", UserRole.RESIDENT);
        createGame(master, "Своя игра в наборе", "Описание.", GameSystem.VERBAL,
                GameVisibility.OPEN, GameStatus.RECRUITING);
        createGame(master, "Своя завершённая игра", "Описание.", GameSystem.VERBAL,
                GameVisibility.OPEN, GameStatus.CLOSED);
        Game joined = createGame(other, "Чужая игра в наборе", "Описание.", GameSystem.VERBAL,
                GameVisibility.OPEN, GameStatus.RECRUITING);
        applyFor("auto_rpg_player_closed", joined, "Участник");
        Long appId = applicationRepository.findByGameIdAndUserId(joined.getId(), player.getId()).orElseThrow().getId();
        mockMvc.perform(post("/rpg/games/" + joined.getId() + "/applications/" + appId + "/accept")
                        .session(login("auto_rpg_other_closed")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        String page = mockMvc.perform(get("/rpg/games").param("search", "игра ")
                        .session(login("auto_rpg_player_closed")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        String mineSection = page.substring(page.indexOf("data-section=\"mine\""),
                page.indexOf("data-section=\"others\""));
        // Завершённой игры в «Моих» нет — она попадает в общий список.
        assertThat(mineSection).doesNotContain("Своя завершённая игра");
        assertThat(mineSection).contains("Чужая игра в наборе");
        String othersSection = page.substring(page.indexOf("data-section=\"others\""));
        assertThat(othersSection).contains("Своя завершённая игра");
        assertThat(othersSection).doesNotContain("Чужая игра в наборе");
    }

    @Test
    void ownGamesAreShownOnFirstPageEvenWhenSortedLast() throws Exception {
        User viewer = createUser("auto_rpg_player_first", UserRole.RESIDENT);
        User other = createUser("auto_rpg_other_first", UserRole.RESIDENT);

        // 12 чужих игр в наборе — по общей сортировке они идут раньше игры зрителя.
        for (int i = 0; i < 12; i++) {
            createGame(other, "Чужая игра " + i, "Описание.", GameSystem.VERBAL,
                    GameVisibility.OPEN, GameStatus.RECRUITING);
        }
        Game mine = createGame(viewer, "Моя идущая игра", "Описание.", GameSystem.VERBAL,
                GameVisibility.OPEN, GameStatus.ACTIVE);

        String first = mockMvc.perform(get("/rpg/games").session(login("auto_rpg_player_first")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(first).contains("Мои игры");
        String mineSection = first.substring(first.indexOf("data-section=\"mine\""));
        assertThat(mineSection).contains("Моя идущая игра");
        // Раздел «Мои игры» — до пагинации общего списка, то есть на первой странице.
        assertThat(first.indexOf("data-section=\"mine\"")).isLessThan(first.indexOf("data-section=\"others\""));
        // Общий список постраничный: 10 чужих игр на странице.
        assertThat(countOccurrences(first.substring(first.indexOf("data-section=\"others\"")),
                "data-game-row=")).isEqualTo(10);

        // На последней странице общего списка раздел «Мои игры» остаётся на месте
        // и своя игра не теряется.
        String last = mockMvc.perform(get("/rpg/games").param("page", "1").session(login("auto_rpg_player_first")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(last.indexOf("data-section=\"mine\"")).isLessThan(last.indexOf("data-section=\"others\""));
        assertThat(countOccurrences(last, "data-game-row=")).isEqualTo(1 + 2);
        assertThat(mine.getStatus()).isEqualTo(GameStatus.ACTIVE);
    }

    @Test
    void pendingApplicationsAreVisibleToEveryViewerWithoutReviewButtons() throws Exception {
        User master = createUser("auto_rpg_master_allapps", UserRole.RESIDENT);
        createUser("auto_rpg_cand_allapps", UserRole.RESIDENT);
        Game game = createGameFor(master, "Заявки всем", GameSystem.VERBAL, GameVisibility.OPEN);
        applyFor("auto_rpg_cand_allapps", game, "Кандидат");

        // Заявку видит посторонний resident, но ревьюить её не может.
        String page = mockMvc.perform(get("/rpg/games/" + game.getId())
                        .session(login("auto_rpg_cand_allapps")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(page).contains("Лист: Кандидат");
        assertThat(page).doesNotContain("/accept");
        assertThat(page).doesNotContain("/reject");
    }

    @Test
    void playerCanWithdrawOwnPendingApplicationOnly() throws Exception {
        User master = createUser("auto_rpg_master_wd", UserRole.RESIDENT);
        User p1 = createUser("auto_rpg_player_wd1", UserRole.RESIDENT);
        User p2 = createUser("auto_rpg_player_wd2", UserRole.RESIDENT);
        Game game = createGameFor(master, "Отзыв заявки", GameSystem.VERBAL, GameVisibility.OPEN);
        applyFor("auto_rpg_player_wd1", game, "Отзывающий");
        Long appId = applicationRepository.findByGameIdAndUserId(game.getId(), p1.getId()).orElseThrow().getId();

        // Чужую заявку отозвать нельзя.
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/applications/" + appId + "/withdraw")
                        .session(login("auto_rpg_player_wd2")).with(csrf()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/applications/" + appId + "/withdraw")
                        .session(login("auto_rpg_player_wd1")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(applicationRepository.findById(appId).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.WITHDRAWN);
        // Лист персонажа сохраняется, чтобы игрок мог подать заявку снова.
        assertThat(sheetRepository.findById(sheetIdFor(game, p1.getId()))).isPresent();

        // Отозванная заявка больше не показывается в списке.
        String page = mockMvc.perform(get("/rpg/games/" + game.getId()).session(login("auto_rpg_master_wd")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(page).contains("Заявок пока нет");

        // После отзыва можно подать заявку заново — строка заявки переиспользуется.
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/apply").session(login("auto_rpg_player_wd1")).with(csrf())
                        .param("name", "Отзывающий")
                        .param("kventa", "Передумал, но вернулся."))
                .andExpect(status().is3xxRedirection());
        assertThat(applicationRepository.findByGameIdAndUserId(game.getId(), p1.getId()).orElseThrow().getStatus())
                .isEqualTo(ApplicationStatus.PENDING);
    }

    @Test
    void playerLeavesGameAndMasterRemovesAcceptedPlayer() throws Exception {
        User master = createUser("auto_rpg_master_leave", UserRole.RESIDENT);
        User p1 = createUser("auto_rpg_player_leave1", UserRole.RESIDENT);
        User p2 = createUser("auto_rpg_player_leave2", UserRole.RESIDENT);
        Game game = createGameFor(master, "Покидание игры", GameSystem.VERBAL, GameVisibility.OPEN);
        applyFor("auto_rpg_player_leave1", game, "Уходящий");
        applyFor("auto_rpg_player_leave2", game, "Оставшийся");
        MockHttpSession masterSession = login("auto_rpg_master_leave");

        Long app1 = applicationRepository.findByGameIdAndUserId(game.getId(), p1.getId()).orElseThrow().getId();
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/applications/" + app1 + "/accept")
                        .session(masterSession).with(csrf()))
                .andExpect(status().is3xxRedirection());
        Long sheet1 = sheetIdFor(game, p1.getId());
        assertThat(sheetRepository.findById(sheet1)).isPresent();

        // Игрок уходит сам.
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/leave")
                        .session(login("auto_rpg_player_leave1")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(sheetRepository.findById(sheet1)).isEmpty();
        // Заявка уходит каскадом вместе с листом — игрок больше не участник.
        // Проверяем на уровне БД: тест классовый @Transactional, JPA отдал бы кэш,
        // а удаление листа ещё не сброшено в базу.
        entityManager.flush();
        assertThat(countRows("rpg_applications", "id = " + app1)).isZero();

        // Ведущий удаляет другого принятого игрока.
        Long app2 = applicationRepository.findByGameIdAndUserId(game.getId(), p2.getId()).orElseThrow().getId();
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/applications/" + app2 + "/accept")
                        .session(masterSession).with(csrf()))
                .andExpect(status().is3xxRedirection());
        Long sheet2 = sheetIdFor(game, p2.getId());

        // Чужой игрок удалить чужого игрока не может.
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/characters/" + sheet2 + "/remove")
                        .session(login("auto_rpg_player_leave1")).with(csrf()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/characters/" + sheet2 + "/remove")
                        .session(masterSession).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(sheetRepository.findById(sheet2)).isEmpty();
    }

    @Test
    void gamePageShowsStatusAndSystemOnOwnRowsAndDropsAccessBadge() throws Exception {
        User master = createUser("auto_rpg_master_layout", UserRole.RESIDENT);
        Game game = createGameFor(master, "Вёрстка игры", GameSystem.VERBAL, GameVisibility.OPEN);

        String page = mockMvc.perform(get("/rpg/games/" + game.getId()).session(login("auto_rpg_master_layout")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        // Статус и система — отдельными строками, а не бейджами в заголовке.
        assertThat(page).contains("Набор игроков");
        assertThat(page).contains("Ведущий:");
        assertThat(page).doesNotContain("align-middle\"><i class=\"bi bi-megaphone");
        // Бейдж типа доступа убран.
        assertThat(page).doesNotContain("Открытая");
    }

    @Test
    void gamePageShowsCustomSystemNameInsteadOfGenericLabel() throws Exception {
        User master = createUser("auto_rpg_master_sysname", UserRole.RESIDENT);
        MockHttpSession session = login("auto_rpg_master_sysname");
        Long sysId = createLibrarySystemWithTemplate(master, "Нетронутое небо");

        mockMvc.perform(post("/rpg/games").session(session).with(csrf())
                        .param("title", "Игра под системой")
                        .param("description", "Описание.")
                        .param("system", "CUSTOM")
                        .param("customSystemId", String.valueOf(sysId))
                        .param("visibility", "OPEN")
                        .param("status", "RECRUITING"))
                .andExpect(status().is3xxRedirection());

        String page = mockMvc.perform(get("/rpg/games/" + gameIdByTitle("Игра под системой")).session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(page).contains("Нетронутое небо");
        assertThat(page).doesNotContain("Кастомная");
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
    void notificationsFollowApplicationLifecycle() throws Exception {
        User master = createUser("auto_rpg_master_n1", UserRole.RESIDENT);
        createUser("auto_rpg_player_n1", UserRole.RESIDENT);
        Game game = createGameFor(master, "Уведомления: заявки", GameSystem.VERBAL, GameVisibility.OPEN);
        MockHttpSession masterSession = login("auto_rpg_master_n1");
        MockHttpSession playerSession = login("auto_rpg_player_n1");

        Long playerId = applyFor("auto_rpg_player_n1", game, "Воин");
        awaitNotification(master.getId(), "RPG_APPLICATION_SUBMITTED");

        Long appId = applicationRepository.findByGameIdAndUserId(game.getId(), playerId).orElseThrow().getId();
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/applications/" + appId + "/withdraw")
                        .session(playerSession).with(csrf()))
                .andExpect(status().is3xxRedirection());
        awaitNotification(master.getId(), "RPG_APPLICATION_WITHDRAWN");

        applyFor("auto_rpg_player_n1", game, "Воин");
        acceptFor(game, playerId);
        awaitNotification(playerId, "RPG_APPLICATION_ACCEPTED");

    }

    @Test
    void notificationFollowsPlayerLeavingAndRejection() throws Exception {
        User master = createUser("auto_rpg_master_n4", UserRole.RESIDENT);
        createUser("auto_rpg_player_n4", UserRole.RESIDENT);
        Game game = createGameFor(master, "Уведомления: уход", GameSystem.VERBAL, GameVisibility.OPEN);
        MockHttpSession playerSession = login("auto_rpg_player_n4");

        applyFor("auto_rpg_player_n4", game, "Бродяга");
        acceptFor(game, userRepository.findByUsername("auto_rpg_player_n4").orElseThrow().getId());
        awaitNotification(userRepository.findByUsername("auto_rpg_player_n4").orElseThrow().getId(),
                "RPG_APPLICATION_ACCEPTED");

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/leave").session(playerSession).with(csrf()))
                .andExpect(status().is3xxRedirection());
        awaitNotification(master.getId(), "RPG_GAME_PLAYER_LEFT");

        // Отклонение заявки проверяется на отдельной игре: после ухода заявка и
        // лист удаляются каскадом, и в тестовой схеме без каскада повторная
        // подача недоступна.
        Game other = createGameFor(master, "Уведомления: отказ", GameSystem.VERBAL, GameVisibility.OPEN);
        applyFor("auto_rpg_player_n4", other, "Бродяга");
        Long playerId = userRepository.findByUsername("auto_rpg_player_n4").orElseThrow().getId();
        Long appId = applicationRepository.findByGameIdAndUserId(other.getId(), playerId).orElseThrow().getId();
        mockMvc.perform(post("/rpg/games/" + other.getId() + "/applications/" + appId + "/reject")
                        .session(login("auto_rpg_master_n4")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        awaitNotification(playerId, "RPG_APPLICATION_REJECTED");
        assertThat(notificationText(playerId, "RPG_APPLICATION_REJECTED")).contains("отклонена");
    }

    @Test
    void notificationsFollowGameCreationAndStatusChange() throws Exception {
        User master = createUser("auto_rpg_master_n2", UserRole.RESIDENT);
        User follower = createUser("auto_rpg_follower_n2", UserRole.RESIDENT);
        createUser("auto_rpg_player_n2", UserRole.RESIDENT);
        // Подписку создаём в отдельной транзакции: запись в транзакции теста
        // не видна асинхронному слушателю событий.
        org.springframework.transaction.support.TransactionTemplate subscriptionTx =
                new org.springframework.transaction.support.TransactionTemplate(txManager);
        subscriptionTx.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        subscriptionTx.executeWithoutResult(status -> jdbcTemplate.update(
                "insert into subscriptions (subscriber_id, target_user_id) values (?, ?)",
                follower.getId(), master.getId()));
        MockHttpSession masterSession = login("auto_rpg_master_n2");

        mockMvc.perform(post("/rpg/games").session(masterSession).with(csrf())
                        .param("title", "Уведомления: статус")
                        .param("description", "Игра для проверки уведомлений.")
                        .param("system", "VERBAL")
                        .param("visibility", "OPEN")
                        .param("status", "RECRUITING"))
                .andExpect(status().is3xxRedirection());
        Game game = gameRepository.findByMasterIdOrderByCreatedAtDesc(master.getId()).get(0);
        awaitNotification(follower.getId(), "RPG_GAME_CREATED");

        Long playerId = applyFor("auto_rpg_player_n2", game, "Маг");
        acceptFor(game, playerId);
        awaitNotification(playerId, "RPG_APPLICATION_ACCEPTED");

        mockMvc.perform(post("/rpg/games/" + game.getId() + "/edit").session(masterSession).with(csrf())
                        .param("title", game.getTitle())
                        .param("description", "Идёт игра.")
                        .param("system", "VERBAL")
                        .param("visibility", "OPEN")
                        .param("status", "ACTIVE")
                        .param("attributesVisible", "true"))
                .andExpect(status().is3xxRedirection());
        awaitNotification(playerId, "RPG_GAME_STATUS_CHANGED");
        assertThat(notificationText(playerId, "RPG_GAME_STATUS_CHANGED"))
                .contains("Набор игроков").contains("В процессе");
    }

    @Test
    void notificationsFollowRoomPostsAndRemoval() throws Exception {
        User master = createUser("auto_rpg_master_n3", UserRole.RESIDENT);
        createUser("auto_rpg_player_n3", UserRole.RESIDENT);
        Game game = createGameFor(master, "Уведомления: комнаты", GameSystem.VERBAL, GameVisibility.OPEN);
        MockHttpSession masterSession = login("auto_rpg_master_n3");

        Long playerId = applyFor("auto_rpg_player_n3", game, "Разведчик");
        acceptFor(game, playerId);
        awaitNotification(playerId, "RPG_APPLICATION_ACCEPTED");

        MvcResult roomRes = mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms")
                        .session(masterSession).with(csrf())
                        .param("title", "Таверна")
                        .param("privateRoom", "false"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        Long roomId = Long.parseLong(roomRes.getResponse().getRedirectedUrl().replaceAll(".*rooms/", ""));

        // Пост ведущего уведомляет игрока.
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms/" + roomId + "/posts")
                        .session(masterSession).with(csrf())
                        .param("content", "Собираемся в таверне."))
                .andExpect(status().is3xxRedirection());
        awaitNotification(playerId, "RPG_ROOM_POST");
        assertThat(notificationText(playerId, "RPG_ROOM_POST")).contains("Таверна");

        // Пост игрока уведомляет ведущего.
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/rooms/" + roomId + "/posts")
                        .session(login("auto_rpg_player_n3")).with(csrf())
                        .param("content", "Иду."))
                .andExpect(status().is3xxRedirection());
        awaitNotification(master.getId(), "RPG_ROOM_POST");

        // Автор своего поста уведомления не получает.
        assertThat(notificationCount(playerId, "RPG_ROOM_POST")).isEqualTo(1);

        Long sheetId = applicationRepository.findByGameIdAndUserId(game.getId(), playerId)
                .orElseThrow().getSheetId();
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/characters/" + sheetId + "/remove")
                        .session(masterSession).with(csrf()))
                .andExpect(status().is3xxRedirection());
        awaitNotification(playerId, "RPG_GAME_PLAYER_REMOVED");
    }

    /**
     * Ждёт уведомление нужного типа: слушатель событий асинхронный. Читаем
     * напрямую из БД — тест классовый @Transactional, JPA отдала бы кэш.
     * <p>
     * Бюджет ожидания — 10 секунд: событие доставляется отдельным потоком, и на
     * загруженной машине (весь класс из 107 тестов) очередь асинхронных задач
     * разворачивается дольше, чем за 5 секунд.
     */
    private void awaitNotification(Long userId, String type) throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            if (notificationCount(userId, type) > 0) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Уведомление " + type + " не пришло пользователю " + userId);
    }

    private int notificationCount(Long userId, String type) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from notifications where user_id = ? and type = ?",
                Integer.class, userId, type);
        return count == null ? 0 : count;
    }

    private String notificationText(Long userId, String type) {
        return jdbcTemplate.queryForObject(
                "select text from notifications where user_id = ? and type = ? order by id desc limit 1",
                String.class, userId, type);
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
        // Текстовое поле без ручного ввода требует список вариантов, поэтому
        // свободный текст создаётся с флагом ручного ввода.
        mockMvc.perform(post("/rpg/games/" + game.getId() + "/attributes").session(masterSession).with(csrf())
                        .param("name", "Тень")
                        .param("attributeType", "TEXT")
                        .param("manualInputAllowed", "true"))
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
    private String textOf(Long sheetId, String key) {
        return attributeRepository.findBySheetIdAndKey(sheetId, key).map(SheetAttribute::getTextValue).orElse(null);
    }

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

    /**
     * Все поля отрендеренной формы: пары «имя → значение», как их отправит
     * браузер. Файловые поля и CSRF пропускаются.
     */
    private MultiValueMap<String, String> formFields(String html) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("<input\\b[^>]*>").matcher(html);
        while (matcher.find()) {
            String tag = matcher.group();
            if (tag.contains("type=\"file\"") || tag.contains("type=\"submit\"")) {
                continue;
            }
            String name = attr(tag, "name");
            if (name.isEmpty() || name.equals("_csrf")) {
                continue;
            }
            form.add(name, attr(tag, "value"));
        }
        return form;
    }

    private String attr(String tag, String name) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile(name + "=\"([^\"]*)\"").matcher(tag);
        return matcher.find() ? matcher.group(1) : "";
    }

    private void setField(MultiValueMap<String, String> form, int index, String field, String value) {
        form.put("attributes[" + index + "]." + field, List.of(value));
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