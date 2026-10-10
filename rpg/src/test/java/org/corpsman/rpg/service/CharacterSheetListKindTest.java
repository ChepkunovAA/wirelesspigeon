package org.corpsman.rpg.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.corpsman.auth.repository.UserRepository;
import org.corpsman.common.service.FileStorageService;
import org.corpsman.rpg.dto.AttributeViewDto;
import org.corpsman.rpg.dto.CharacterSheetViewDto;
import org.corpsman.rpg.dto.SheetAttributeForm;
import org.corpsman.rpg.dto.SheetForm;
import org.corpsman.rpg.model.AttributeType;
import org.corpsman.rpg.model.CharacterSheet;
import org.corpsman.rpg.model.Game;
import org.corpsman.rpg.model.GameSystem;
import org.corpsman.rpg.model.ListItem;
import org.corpsman.rpg.model.ListKind;
import org.corpsman.rpg.model.SheetAttribute;
import org.corpsman.rpg.repository.CharacterSheetRepository;
import org.corpsman.rpg.repository.GameApplicationRepository;
import org.corpsman.rpg.repository.GameRepository;
import org.corpsman.rpg.repository.SheetAttributeRepository;
import org.corpsman.rpg.service.character.AttributeSpec;
import org.corpsman.rpg.service.character.CharacterSystem;
import org.corpsman.rpg.service.character.CharacterSystemRegistry;
import org.corpsman.rpg.service.character.DndCharacterSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Юнит-тесты типов списков листа: «Простой список» (строки) и «Сложный список»
 * (заголовок + описание + уровень). Инвентарь всегда простой; в D&D умения,
 * черты и заклинания — сложные.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CharacterSheetListKindTest {

    @Mock
    private CharacterSheetRepository sheetRepository;

    @Mock
    private SheetAttributeRepository attributeRepository;

    @Mock
    private GameApplicationRepository applicationRepository;

    @Mock
    private GameAccessService accessService;

    @Mock
    private CharacterSystemRegistry systemRegistry;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GameRepository gameRepository;

    @Mock
    private CharacterSystem system;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private CharacterSheetService service;

    private Game game;

    private CharacterSheet sheet;

    @BeforeEach
    void setUp() {
        service = new CharacterSheetService(sheetRepository, attributeRepository, applicationRepository,
                accessService, systemRegistry, fileStorageService, userRepository, gameRepository, objectMapper);

        game = new Game();
        game.setId(7L);
        game.setSystem(GameSystem.DND);
        game.setMasterId(100L);
        game.setAttributesVisible(true);

        sheet = new CharacterSheet();
        sheet.setId(50L);
        sheet.setGameId(7L);
        sheet.setOwnerId(100L);
        sheet.setName("Герой");

        when(systemRegistry.get(GameSystem.DND)).thenReturn(system);
        when(system.template(any())).thenReturn(List.of(
                new AttributeSpec("features", "Черты", AttributeType.LIST, 510, null, ListKind.COMPLEX),
                new AttributeSpec("spells", "Заклинания", AttributeType.LIST, 530, null, ListKind.COMPLEX)));
        when(system.computedModifiers(any(), any())).thenReturn(Map.of());
        when(sheetRepository.save(any(CharacterSheet.class))).thenAnswer(inv -> inv.getArgument(0));
        when(attributeRepository.save(any(SheetAttribute.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(anyLong())).thenReturn(Optional.empty());
        when(accessService.isMaster(any(), any())).thenReturn(true);
        when(gameRepository.findById(7L)).thenReturn(Optional.of(game));
    }

    private static SheetAttribute attr(String key, String name, AttributeType type, int position) {
        SheetAttribute a = new SheetAttribute();
        a.setId((long) key.hashCode());
        a.setSheetId(50L);
        a.setKey(key);
        a.setName(name);
        a.setType(type);
        a.setPosition(position);
        return a;
    }

    private List<String> parseStrings(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            throw new AssertionError("ожидался массив строк, получено: " + json, e);
        }
    }

    private List<ListItem> parseItems(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<ListItem>>() {
            });
        } catch (Exception e) {
            throw new AssertionError("ожидался массив объектов, получено: " + json, e);
        }
    }

    private SheetAttribute savedAttr(String key) {
        ArgumentCaptor<SheetAttribute> captor = ArgumentCaptor.forClass(SheetAttribute.class);
        verify(attributeRepository, atLeastOnce()).save(captor.capture());
        for (SheetAttribute a : captor.getAllValues()) {
            if (key.equals(a.getKey())) {
                return a;
            }
        }
        throw new AssertionError("Атрибут " + key + " не сохранён");
    }

    private void createWith(SheetAttributeForm attrForm) {
        SheetForm form = new SheetForm();
        form.setName("Герой");
        form.setAttributes(List.of(attrForm));
        service.create(game, 100L, false, form, null);
    }

    private static SheetAttributeForm listForm(String key, String name, ListKind kind) {
        SheetAttributeForm f = new SheetAttributeForm();
        f.setKey(key);
        f.setName(name);
        f.setType(AttributeType.LIST);
        f.setListKind(kind);
        return f;
    }

    private AttributeViewDto viewOf(CharacterSheetViewDto dto, String key) {
        return dto.getAttributes().stream().filter(a -> key.equals(a.getKey()))
                .findFirst().orElseThrow();
    }

    // --- спецификация типа списка ---

    @Test
    void inventoryIsAlwaysSimpleList() {
        createWith(listForm("spells", "Заклинания", ListKind.COMPLEX));

        assertThat(savedAttr(CharacterSheetService.KEY_INVENTORY).getListKind())
                .isEqualTo(ListKind.SIMPLE);
    }

    @Test
    void dndSpellAndFeatureListsAreComplex() {
        createWith(listForm("spells", "Заклинания", ListKind.COMPLEX));

        assertThat(savedAttr("spells").getListKind()).isEqualTo(ListKind.COMPLEX);
    }

    /**
     * Регрессия: миграция 3.1.0-02 проставила list_kind='SIMPLE' всем
     * существующим спискам, включая сложные списки D&D. Вид списка обязан
     * определяться системой, а не сохранённым значением.
     */
    @Test
    void staleSimpleStampFromMigrationIsOverriddenBySystemTemplate() {
        for (String key : new String[]{"spells", "features"}) {
            SheetAttribute attr = attr(key, key, AttributeType.LIST, 600);
            attr.setListKind(ListKind.SIMPLE);
            attr.setListValues("[]");
            when(attributeRepository.findBySheetIdOrderByPositionAsc(50L)).thenReturn(List.of(attr));

            assertThat(viewOf(service.render(game, sheet, null, 100L), key).getListKind())
                    .as("список %s должен быть сложным", key)
                    .isEqualTo(ListKind.COMPLEX);
        }
    }

    @Test
    void staleSimpleStampIsRepairedOnSave() {
        SheetAttribute attr = attr("spells", "Заклинания", AttributeType.LIST, 600);
        attr.setListKind(ListKind.SIMPLE);
        when(attributeRepository.findBySheetIdOrderByPositionAsc(50L)).thenReturn(List.of(attr));

        SheetForm form = new SheetForm();
        form.setName("Герой");
        form.setAttributes(List.of(listForm("spells", "Заклинания", ListKind.SIMPLE)));
        service.create(game, 100L, false, form, null);

        assertThat(savedAttr("spells").getListKind()).isEqualTo(ListKind.COMPLEX);
    }

    @Test
    void inventoryKeepsSimpleEvenWithComplexStampInForm() {
        SheetAttributeForm f = listForm("inventory", "Инвентарь", ListKind.SIMPLE);
        f.setListText("Факел");

        createWith(f);

        assertThat(savedAttr("inventory").getListKind()).isEqualTo(ListKind.SIMPLE);
    }

    // --- сохранение простого списка ---

    @Test
    void simpleListIsStoredAsArrayOfStrings() {
        SheetAttributeForm f = listForm("inventory", "Инвентарь", ListKind.SIMPLE);
        f.setListText("Факел\nХлеб");

        createWith(f);

        SheetAttribute saved = savedAttr("inventory");
        assertThat(saved.getListKind()).isEqualTo(ListKind.SIMPLE);
        assertThat(parseStrings(saved.getListValues())).containsExactly("Факел", "Хлеб");
    }

    @Test
    void simpleListDropsBlankLines() {
        SheetAttributeForm f = listForm("inventory", "Инвентарь", ListKind.SIMPLE);
        f.setListText("Факел\n\n   \nХлеб");

        createWith(f);

        assertThat(parseStrings(savedAttr("inventory").getListValues()))
                .containsExactly("Факел", "Хлеб");
    }

    @Test
    void simpleListTrimsItems() {
        SheetAttributeForm f = listForm("inventory", "Инвентарь", ListKind.SIMPLE);
        f.setListText("  Факел  ");

        createWith(f);

        assertThat(parseStrings(savedAttr("inventory").getListValues())).containsExactly("Факел");
    }

    // --- сохранение сложного списка ---

    @Test
    void complexListIsStoredAsArrayOfObjects() {
        SheetAttributeForm f = listForm("spells", "Заклинания", ListKind.COMPLEX);
        f.setListJson("[{\"header\":\"Огненный шар\",\"description\":\"Урон 8d6\",\"level\":3}]");

        createWith(f);

        SheetAttribute saved = savedAttr("spells");
        assertThat(saved.getListKind()).isEqualTo(ListKind.COMPLEX);
        List<ListItem> items = parseItems(saved.getListValues());
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getHeader()).isEqualTo("Огненный шар");
        assertThat(items.get(0).getDescription()).isEqualTo("Урон 8d6");
        assertThat(items.get(0).getLevel()).isEqualTo(3);
    }

    @Test
    void complexListNormalizesLevelAndDropsBlankHeaders() {
        SheetAttributeForm f = listForm("spells", "Заклинания", ListKind.COMPLEX);
        f.setListJson("[{\"header\":\"  \",\"description\":\"мусор\"},"
                + "{\"header\":\"Щит\",\"description\":\"\",\"level\":null},"
                + "{\"header\":\"Огонь\",\"description\":\"\",\"level\":42}]");

        createWith(f);

        List<ListItem> items = parseItems(savedAttr("spells").getListValues());
        assertThat(items).extracting(ListItem::getHeader).containsExactly("Щит", "Огонь");
        assertThat(items).extracting(ListItem::getLevel).containsExactly(1, 9);
    }

    // --- чтение в рендере ---

    @Test
    void renderKeepsBothListFormats() {
        SheetAttribute inventory = attr("inventory", "Инвентарь", AttributeType.LIST, 540);
        inventory.setListKind(ListKind.SIMPLE);
        inventory.setListValues("[\"Факел\",\"Хлеб\"]");
        SheetAttribute spells = attr("spells", "Заклинания", AttributeType.LIST, 530);
        spells.setListKind(ListKind.COMPLEX);
        spells.setListValues("[{\"header\":\"Огненный шар\",\"description\":\"Урон 8d6\",\"level\":3}]");
        when(attributeRepository.findBySheetIdOrderByPositionAsc(50L)).thenReturn(List.of(inventory, spells));

        CharacterSheetViewDto dto = service.render(game, sheet, null, 100L);

        AttributeViewDto inv = viewOf(dto, "inventory");
        assertThat(inv.getListKind()).isEqualTo(ListKind.SIMPLE);
        assertThat(inv.getListItems()).extracting(ListItem::getHeader).containsExactly("Факел", "Хлеб");
        assertThat(inv.getListItems()).allSatisfy(i -> assertThat(i.getDescription()).isEmpty());

        AttributeViewDto sp = viewOf(dto, "spells");
        assertThat(sp.getListKind()).isEqualTo(ListKind.COMPLEX);
        assertThat(sp.getListItems().get(0).getLevel()).isEqualTo(3);
    }

    @Test
    void renderInfersComplexKindForLegacyObjectJson() {
        // Старый лист: list_kind == null, а JSON — массив объектов.
        SheetAttribute spells = attr("spells", "Заклинания", AttributeType.LIST, 530);
        spells.setListValues("[{\"header\":\"Щит\",\"description\":\"AC +2\",\"level\":1}]");
        when(attributeRepository.findBySheetIdOrderByPositionAsc(50L)).thenReturn(List.of(spells));

        CharacterSheetViewDto dto = service.render(game, sheet, null, 100L);

        assertThat(viewOf(dto, "spells").getListKind()).isEqualTo(ListKind.COMPLEX);
    }

    @Test
    void legacyStringJsonKeepsKindDeclaredBySystem() {
        // list_kind = null (лист создан до появления колонки): источник истины —
        // шаблон системы, а не формат JSON.
        SheetAttribute features = attr("features", "Черты", AttributeType.LIST, 510);
        features.setListValues("[\"Второе дыхание\"]");
        when(attributeRepository.findBySheetIdOrderByPositionAsc(50L)).thenReturn(List.of(features));

        assertThat(viewOf(service.render(game, sheet, null, 100L), "features").getListKind())
                .isEqualTo(ListKind.COMPLEX);
    }

    @Test
    void legacyEmptyListTakesKindFromSystemTemplate() {
        // Пустой JSON не даёт признака формата — вид берётся из шаблона.
        SheetAttribute spells = attr("spells", "Заклинания", AttributeType.LIST, 530);
        spells.setListValues(null);
        when(attributeRepository.findBySheetIdOrderByPositionAsc(50L)).thenReturn(List.of(spells));

        assertThat(viewOf(service.render(game, sheet, null, 100L), "spells").getListKind())
                .isEqualTo(ListKind.COMPLEX);
    }

    @Test
    void legacyEmptyInventoryStaysSimple() {
        SheetAttribute inventory = attr("inventory", "Инвентарь", AttributeType.LIST, 540);
        inventory.setListValues(null);
        when(attributeRepository.findBySheetIdOrderByPositionAsc(50L)).thenReturn(List.of(inventory));

        assertThat(viewOf(service.render(game, sheet, null, 100L), "inventory").getListKind())
                .isEqualTo(ListKind.SIMPLE);
    }

    @Test
    void renderInfersSimpleKindForEmptyListJson() {
        SheetAttribute inventory = attr("inventory", "Инвентарь", AttributeType.LIST, 540);
        inventory.setListValues(null);
        when(attributeRepository.findBySheetIdOrderByPositionAsc(50L)).thenReturn(List.of(inventory));

        assertThat(viewOf(service.render(game, sheet, null, 100L), "inventory").getListKind())
                .isEqualTo(ListKind.SIMPLE);
    }

    // --- правка инвентаря из режима просмотра ---

    @Test
    void addInventoryItemAppendsPlainString() {
        SheetAttribute inventory = attr("inventory", "Инвентарь", AttributeType.LIST, 540);
        inventory.setListKind(ListKind.SIMPLE);
        inventory.setListValues("[\"Факел\"]");
        when(attributeRepository.findBySheetIdAndKey(50L, "inventory")).thenReturn(Optional.of(inventory));

        service.addInventoryItem(sheet, "Хлеб", "игнорируется");

        assertThat(inventory.getListKind()).isEqualTo(ListKind.SIMPLE);
        assertThat(parseStrings(inventory.getListValues())).containsExactly("Факел", "Хлеб");
    }

    @Test
    void addInventoryItemToMissingAttributeCreatesSimpleList() {
        when(attributeRepository.findBySheetIdAndKey(50L, "inventory")).thenReturn(Optional.empty());

        service.addInventoryItem(sheet, "Факел", null);

        SheetAttribute saved = savedAttr("inventory");
        assertThat(saved.getType()).isEqualTo(AttributeType.LIST);
        assertThat(saved.getListKind()).isEqualTo(ListKind.SIMPLE);
        assertThat(saved.getName()).isEqualTo("Инвентарь");
        assertThat(parseStrings(saved.getListValues())).containsExactly("Факел");
    }

    @Test
    void addInventoryItemIgnoresBlankHeader() {
        SheetAttribute inventory = attr("inventory", "Инвентарь", AttributeType.LIST, 540);
        inventory.setListKind(ListKind.SIMPLE);
        inventory.setListValues("[]");
        when(attributeRepository.findBySheetIdAndKey(50L, "inventory")).thenReturn(Optional.of(inventory));

        service.addInventoryItem(sheet, "   ", null);

        assertThat(parseStrings(inventory.getListValues())).isEmpty();
        verify(attributeRepository, org.mockito.Mockito.never()).save(any(SheetAttribute.class));
    }

    @Test
    void removeInventoryItemRemovesByIndex() {
        SheetAttribute inventory = attr("inventory", "Инвентарь", AttributeType.LIST, 540);
        inventory.setListKind(ListKind.SIMPLE);
        inventory.setListValues("[\"Факел\",\"Хлеб\",\"Ром\"]");
        when(attributeRepository.findBySheetIdAndKey(50L, "inventory")).thenReturn(Optional.of(inventory));

        service.removeInventoryItem(sheet, 1);

        assertThat(parseStrings(inventory.getListValues())).containsExactly("Факел", "Ром");
    }

    @Test
    void removeInventoryItemKeepsLegacyObjectJsonReadable() {
        SheetAttribute inventory = attr("inventory", "Инвентарь", AttributeType.LIST, 540);
        inventory.setListValues("[{\"header\":\"Факел\",\"description\":\"свет\"}]");
        when(attributeRepository.findBySheetIdAndKey(50L, "inventory")).thenReturn(Optional.of(inventory));

        service.removeInventoryItem(sheet, 0);

        assertThat(parseItems(inventory.getListValues())).isEmpty();
    }

    // --- группировка заклинаний ---

    @Test
    void spellGroupsOnlyForComplexSpellList() {
        SheetAttribute spells = attr("spells", "Заклинания", AttributeType.LIST, 530);
        spells.setListKind(ListKind.COMPLEX);
        spells.setListValues("[{\"header\":\"Огненный шар\",\"description\":\"\",\"level\":3},"
                + "{\"header\":\"Свет\",\"description\":\"\",\"level\":1}]");
        when(attributeRepository.findBySheetIdOrderByPositionAsc(50L)).thenReturn(List.of(spells));

        assertThat(service.render(game, sheet, null, 100L).getSpellGroups()).hasSize(2);
    }

    @Test
    void inventoryIsNeverGroupedAsSpells() {
        SheetAttribute inventory = attr("inventory", "Инвентарь", AttributeType.LIST, 540);
        inventory.setListKind(ListKind.SIMPLE);
        inventory.setListValues("[\"Факел\"]");
        when(attributeRepository.findBySheetIdOrderByPositionAsc(50L)).thenReturn(List.of(inventory));

        assertThat(service.render(game, sheet, null, 100L).getSpellGroups()).isEmpty();
    }

    // --- полный шаблон D&D ---

    @Test
    void dndTemplateMarksSpellListsAsComplex() {
        DndCharacterSystem dnd = new DndCharacterSystem();
        when(system.template(any())).thenReturn(dnd.template(game));
        when(system.computedModifiers(any(), any())).thenReturn(Map.of());

        CharacterSheetViewDto edit = service.renderForEdit(game, sheet);

        assertThat(viewOf(edit, "spells").getListKind()).isEqualTo(ListKind.COMPLEX);
        assertThat(viewOf(edit, "feats").getListKind()).isEqualTo(ListKind.COMPLEX);
        assertThat(viewOf(edit, "skills").getListKind()).isEqualTo(ListKind.COMPLEX);
        assertThat(viewOf(edit, "inventory").getListKind()).isEqualTo(ListKind.SIMPLE);
    }
}
