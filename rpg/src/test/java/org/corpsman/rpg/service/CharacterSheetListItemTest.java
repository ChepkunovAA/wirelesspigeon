package org.corpsman.rpg.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.corpsman.auth.repository.UserRepository;
import org.corpsman.common.service.FileStorageService;
import org.corpsman.rpg.dto.AttributeViewDto;
import org.corpsman.rpg.dto.CharacterSheetViewDto;
import org.corpsman.rpg.dto.SheetAttributeForm;
import org.corpsman.rpg.dto.SheetForm;
import org.corpsman.rpg.dto.SpellGroupDto;
import org.corpsman.rpg.dto.SpellSlotDto;
import org.corpsman.rpg.dto.SpellSlotEditDto;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Юнит-тесты сервисного слоя: списки листа (заголовок/описание/уровень)
 * и единый блок ячеек заклинаний (текущее/максимальное по уровням).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CharacterSheetListItemTest {

    private static final String SPELL_LIST_JSON =
            "[{\"header\":\"Огненный шар\",\"description\":\"Урон 8d6\",\"level\":3},"
                    + "{\"header\":\"Свет\",\"description\":\"Ослепление\",\"level\":1}]";

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
                new AttributeSpec("features", "Черты", AttributeType.LIST, 560, null, ListKind.COMPLEX),
                new AttributeSpec("spells", "Заклинания", AttributeType.LIST, 600, null, ListKind.COMPLEX)));
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

    private List<ListItem> parse(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<ListItem>>() {
            });
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private List<ListItem> storedItems(ArgumentCaptor<SheetAttribute> captor, String key) {
        for (SheetAttribute a : captor.getAllValues()) {
            if (key.equals(a.getKey())) {
                return parse(a.getListValues());
            }
        }
        throw new AssertionError("Атрибут " + key + " не сохранён");
    }

    private Integer storedNumeric(ArgumentCaptor<SheetAttribute> captor, String key) {
        for (SheetAttribute a : captor.getAllValues()) {
            if (key.equals(a.getKey())) {
                return a.getNumericValue();
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

    private static SheetAttributeForm listForm(String key, String name) {
        SheetAttributeForm f = new SheetAttributeForm();
        f.setKey(key);
        f.setName(name);
        f.setType(AttributeType.LIST);
        f.setListKind(ListKind.COMPLEX);
        return f;
    }

    private List<ListItem> itemsOf(CharacterSheetViewDto dto, String key) {
        return dto.getAttributes().stream()
                .filter(a -> key.equals(a.getKey()))
                .findFirst().orElseThrow().getListItems();
    }

    // --- сохранение списков из формы ---

    @Test
    void listJsonFromFormIsStoredWithHeaderDescriptionAndLevel() {
        SheetAttributeForm attrForm = listForm("spells", "Заклинания");
        attrForm.setListJson(SPELL_LIST_JSON);

        createWith(attrForm);

        ArgumentCaptor<SheetAttribute> captor = ArgumentCaptor.forClass(SheetAttribute.class);
        verify(attributeRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        List<ListItem> items = storedItems(captor, "spells");

        assertThat(items).hasSize(2);
        assertThat(items.get(0).getHeader()).isEqualTo("Огненный шар");
        assertThat(items.get(0).getDescription()).isEqualTo("Урон 8d6");
        assertThat(items.get(0).getLevel()).isEqualTo(3);
        assertThat(items.get(1).getHeader()).isEqualTo("Свет");
        assertThat(items.get(1).getLevel()).isEqualTo(1);
    }

    @Test
    void listItemWithoutLevelGetsDefaultLevelOne() {
        SheetAttributeForm attrForm = listForm("spells", "Заклинания");
        attrForm.setListJson("[{\"header\":\"Щит\",\"description\":\"\",\"level\":null}]");

        createWith(attrForm);

        ArgumentCaptor<SheetAttribute> captor = ArgumentCaptor.forClass(SheetAttribute.class);
        verify(attributeRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());

        assertThat(storedItems(captor, "spells").get(0).getLevel()).isEqualTo(1);
    }

    @Test
    void blankHeaderIsDroppedFromList() {
        SheetAttributeForm attrForm = listForm("spells", "Заклинания");
        attrForm.setListJson("[{\"header\":\"  \",\"description\":\"мусор\"},{\"header\":\"Щит\"}]");

        createWith(attrForm);

        ArgumentCaptor<SheetAttribute> captor = ArgumentCaptor.forClass(SheetAttribute.class);
        verify(attributeRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());

        assertThat(storedItems(captor, "spells"))
                .extracting(ListItem::getHeader).containsExactly("Щит");
    }

    @Test
    void legacyJsonOfPlainStringsIsReadableAsListItems() {
        SheetAttribute inventory = attr("inventory", "Инвентарь", AttributeType.LIST, 540);
        inventory.setListValues("[\"Факел\",\"Хлеб\"]");
        SheetAttribute spells = attr("spells", "Заклинания", AttributeType.LIST, 600);
        spells.setListValues(SPELL_LIST_JSON);
        when(attributeRepository.findBySheetIdOrderByPositionAsc(50L)).thenReturn(List.of(inventory, spells));

        CharacterSheetViewDto dto = service.render(game, sheet, null, 100L);

        assertThat(itemsOf(dto, "inventory")).extracting(ListItem::getHeader).containsExactly("Факел", "Хлеб");
        assertThat(itemsOf(dto, "spells")).extracting(ListItem::getLevel).containsExactly(3, 1);
    }

    // --- чтение списков в рендере ---

    @Test
    void renderExposesListItemsInsteadOfEmptyList() {
        SheetAttribute inventory = attr("inventory", "Инвентарь", AttributeType.LIST, 540);
        inventory.setListValues(SPELL_LIST_JSON);
        when(attributeRepository.findBySheetIdOrderByPositionAsc(50L)).thenReturn(List.of(inventory));

        CharacterSheetViewDto dto = service.render(game, sheet, null, 100L);

        AttributeViewDto view = dto.getAttributes().stream()
                .filter(a -> "inventory".equals(a.getKey())).findFirst().orElseThrow();
        assertThat(view.getListItems()).hasSize(2);
        assertThat(view.getListItems().get(1).getHeader()).isEqualTo("Свет");
    }

    // --- ячейки заклинаний ---

    @Test
    void renderBuildsSpellSlotsWithCurrentAndMax() {
        SheetAttribute max = attr("spell_slot_max:1", "Ячейки 1 ур. (макс.)", AttributeType.NUMERIC, 560);
        max.setNumericValue(4);
        SheetAttribute current = attr("spell_slot_current:1", "Ячейки 1 ур. (текущ.)", AttributeType.NUMERIC, 565);
        current.setNumericValue(2);
        when(attributeRepository.findBySheetIdOrderByPositionAsc(50L)).thenReturn(List.of(max, current));

        SpellSlotDto slot = service.render(game, sheet, null, 100L).getSpellSlots().get(0);

        assertThat(slot.getLevel()).isEqualTo(1);
        assertThat(slot.getCurrent()).isEqualTo(2);
        assertThat(slot.getMax()).isEqualTo(4);
    }

    @Test
    void renderSkipsSpellSlotLevelsThatAreFullyZero() {
        SheetAttribute max = attr("spell_slot_max:1", "Ячейки 1 ур. (макс.)", AttributeType.NUMERIC, 560);
        max.setNumericValue(0);
        SheetAttribute current = attr("spell_slot_current:1", "Ячейки 1 ур. (текущ.)", AttributeType.NUMERIC, 565);
        current.setNumericValue(0);
        when(attributeRepository.findBySheetIdOrderByPositionAsc(50L)).thenReturn(List.of(max, current));

        assertThat(service.render(game, sheet, null, 100L).getSpellSlots()).isEmpty();
    }

    @Test
    void renderOrdersSpellSlotsByLevel() {
        SheetAttribute max3 = attr("spell_slot_max:3", "Ячейки 3 ур. (макс.)", AttributeType.NUMERIC, 580);
        max3.setNumericValue(1);
        SheetAttribute max1 = attr("spell_slot_max:1", "Ячейки 1 ур. (макс.)", AttributeType.NUMERIC, 560);
        max1.setNumericValue(4);
        SheetAttribute max2 = attr("spell_slot_max:2", "Ячейки 2 ур. (макс.)", AttributeType.NUMERIC, 570);
        max2.setNumericValue(3);
        when(attributeRepository.findBySheetIdOrderByPositionAsc(50L))
                .thenReturn(List.of(max3, max1, max2));

        assertThat(service.render(game, sheet, null, 100L).getSpellSlots())
                .extracting(SpellSlotDto::getLevel).containsExactly(1, 2, 3);
    }

    @Test
    void renderKeepsCurrentSpellSlotsAboveMaxAndFlagsThem() {
        SheetAttribute max = attr("spell_slot_max:1", "Ячейки 1 ур. (макс.)", AttributeType.NUMERIC, 560);
        max.setNumericValue(2);
        SheetAttribute current = attr("spell_slot_current:1", "Ячейки 1 ур. (текущ.)", AttributeType.NUMERIC, 565);
        current.setNumericValue(5);
        when(attributeRepository.findBySheetIdOrderByPositionAsc(50L)).thenReturn(List.of(max, current));

        SpellSlotDto slot = service.render(game, sheet, null, 100L).getSpellSlots().get(0);

        // Текущее может превышать максимум — значение не подрезается.
        assertThat(slot.getCurrent()).isEqualTo(5);
        assertThat(slot.getMax()).isEqualTo(2);
        assertThat(slot.isOverLimit()).isTrue();
    }

    @Test
    void renderDoesNotFlagSpellSlotsWithinLimit() {
        SheetAttribute max = attr("spell_slot_max:1", "Ячейки 1 ур. (макс.)", AttributeType.NUMERIC, 560);
        max.setNumericValue(4);
        SheetAttribute current = attr("spell_slot_current:1", "Ячейки 1 ур. (текущ.)", AttributeType.NUMERIC, 565);
        current.setNumericValue(2);
        when(attributeRepository.findBySheetIdOrderByPositionAsc(50L)).thenReturn(List.of(max, current));

        assertThat(service.render(game, sheet, null, 100L).getSpellSlots().get(0).isOverLimit()).isFalse();
    }

    @Test
    void editModeKeepsCurrentSpellSlotsAboveMax() {
        SheetAttribute max = attr("spell_slot_max:1", "Ячейки 1 ур. (макс.)", AttributeType.NUMERIC, 560);
        max.setNumericValue(2);
        SheetAttribute current = attr("spell_slot_current:1", "Ячейки 1 ур. (текущ.)", AttributeType.NUMERIC, 565);
        current.setNumericValue(5);
        when(attributeRepository.findBySheetIdOrderByPositionAsc(50L)).thenReturn(List.of(max, current));
        when(system.template(any())).thenReturn(List.of(
                new AttributeSpec("spell_slot_max:1", "Ячейки 1 ур. (макс.)", AttributeType.NUMERIC, 560),
                new AttributeSpec("spell_slot_current:1", "Ячейки 1 ур. (текущ.)", AttributeType.NUMERIC, 565)));
        when(system.computedModifiers(any(), any())).thenReturn(Map.of());

        List<SpellSlotEditDto> edits = service.renderForEdit(game, sheet).getSpellSlotEdits();

        assertThat(edits).hasSize(1);
        assertThat(edits.get(0).getCurrent()).isEqualTo(5);
        assertThat(edits.get(0).getMax()).isEqualTo(2);
    }

    @Test
    void negativeSpellSlotValuesAreTreatedAsZero() {
        SheetAttribute max = attr("spell_slot_max:1", "Ячейки 1 ур. (макс.)", AttributeType.NUMERIC, 560);
        max.setNumericValue(-3);
        SheetAttribute current = attr("spell_slot_current:1", "Ячейки 1 ур. (текущ.)", AttributeType.NUMERIC, 565);
        current.setNumericValue(-1);
        when(attributeRepository.findBySheetIdOrderByPositionAsc(50L)).thenReturn(List.of(max, current));

        assertThat(service.render(game, sheet, null, 100L).getSpellSlots()).isEmpty();
    }

    @Test
    void renderMarksSpellSlotAttributesWithFlag() {
        SheetAttribute max = attr("spell_slot_max:1", "Ячейки 1 ур. (макс.)", AttributeType.NUMERIC, 560);
        max.setNumericValue(1);
        SheetAttribute level = attr("level", "Уровень", AttributeType.NUMERIC, 10);
        when(attributeRepository.findBySheetIdOrderByPositionAsc(50L)).thenReturn(List.of(max, level));

        CharacterSheetViewDto dto = service.render(game, sheet, null, 100L);

        assertThat(dto.getAttributes()).filteredOn(AttributeViewDto::isSpellSlot)
                .extracting(AttributeViewDto::getKey).containsExactly("spell_slot_max:1");
    }

    @Test
    void renderGroupsSpellsByLevelWithAbsoluteIndex() {
        SheetAttribute spells = attr("spells", "Заклинания", AttributeType.LIST, 600);
        spells.setListValues(SPELL_LIST_JSON);
        when(attributeRepository.findBySheetIdOrderByPositionAsc(50L)).thenReturn(List.of(spells));

        List<SpellGroupDto> groups = service.render(game, sheet, null, 100L).getSpellGroups();

        assertThat(groups).extracting(SpellGroupDto::getLevel).containsExactly(1, 3);
        assertThat(groups.get(0).getItems()).hasSize(1);
        assertThat(groups.get(0).getItems().get(0).getHeader()).isEqualTo("Свет");
        assertThat(groups.get(0).getItems().get(0).getIndex()).isEqualTo(1);
        assertThat(groups.get(1).getItems().get(0).getHeader()).isEqualTo("Огненный шар");
        assertThat(groups.get(1).getItems().get(0).getIndex()).isZero();
    }

    @Test
    void renderDoesNotGroupNonSpellLists() {
        SheetAttribute inventory = attr("inventory", "Инвентарь", AttributeType.LIST, 540);
        inventory.setListValues(SPELL_LIST_JSON);
        when(attributeRepository.findBySheetIdOrderByPositionAsc(50L)).thenReturn(List.of(inventory));

        assertThat(service.render(game, sheet, null, 100L).getSpellGroups()).isEmpty();
    }

    @Test
    void editModeShowsAllNineSpellSlotLevelsEvenWhenEmpty() {
        DndCharacterSystem dnd = new DndCharacterSystem();
        when(system.template(any())).thenReturn(dnd.template(game));
        when(system.computedModifiers(any(), any())).thenReturn(Map.of());
        CharacterSheetViewDto edit = service.renderForEdit(game, sheet);

        assertThat(edit.getSpellSlotEdits())
                .extracting(SpellSlotEditDto::getLevel)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9);
        assertThat(edit.getSpellSlotEdits()).allSatisfy(dto -> {
            assertThat(dto.getCurrentAttrIndex()).isNotNegative();
            assertThat(dto.getMaxAttrIndex()).isNotNegative();
            assertThat(dto.getCurrent()).isNotNegative();
            assertThat(dto.getMax()).isNotNegative();
        });
    }

    @Test
    void editModeKeepsCurrentSpellSlotWithinMax() {
        SheetAttribute max = attr("spell_slot_max:2", "Ячейки 2 ур. (макс.)", AttributeType.NUMERIC, 570);
        max.setNumericValue(2);
        SheetAttribute current = attr("spell_slot_current:2", "Ячейки 2 ур. (текущ.)", AttributeType.NUMERIC, 575);
        current.setNumericValue(7);
        when(attributeRepository.findBySheetIdOrderByPositionAsc(50L)).thenReturn(List.of(max, current));
        when(system.template(any())).thenReturn(List.of(
                new AttributeSpec("spell_slot_max:2", "Ячейки 2 ур. (макс.)", AttributeType.NUMERIC, 570),
                new AttributeSpec("spell_slot_current:2", "Ячейки 2 ур. (текущ.)", AttributeType.NUMERIC, 575)));
        when(system.computedModifiers(any(), any())).thenReturn(Map.of());

        List<SpellSlotEditDto> edits = service.renderForEdit(game, sheet).getSpellSlotEdits();

        assertThat(edits).hasSize(1);
        assertThat(edits.get(0).getLevel()).isEqualTo(2);
        assertThat(edits.get(0).getMax()).isEqualTo(2);
        assertThat(edits.get(0).getCurrent()).isEqualTo(7);
    }

    @Test
    void resetSpellSlotsSetsCurrentEqualToMaxAndSkipsEmptyLevels() {
        SheetAttribute max = attr("spell_slot_max:1", "Ячейки 1 ур. (макс.)", AttributeType.NUMERIC, 560);
        max.setNumericValue(4);
        SheetAttribute current = attr("spell_slot_current:1", "Ячейки 1 ур. (текущ.)", AttributeType.NUMERIC, 565);
        current.setNumericValue(0);
        SheetAttribute zeroMax = attr("spell_slot_max:2", "Ячейки 2 ур. (макс.)", AttributeType.NUMERIC, 570);
        zeroMax.setNumericValue(0);
        SheetAttribute zeroCurrent = attr("spell_slot_current:2", "Ячейки 2 ур. (текущ.)", AttributeType.NUMERIC, 575);
        zeroCurrent.setNumericValue(0);
        when(attributeRepository.findBySheetIdOrderByPositionAsc(50L))
                .thenReturn(List.of(max, current, zeroMax, zeroCurrent));

        service.resetSpellSlots(sheet);

        ArgumentCaptor<SheetAttribute> captor = ArgumentCaptor.forClass(SheetAttribute.class);
        verify(attributeRepository).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(SheetAttribute::getKey)
                .containsExactly("spell_slot_current:1");
        assertThat(storedNumeric(captor, "spell_slot_current:1")).isEqualTo(4);
        assertThat(zeroCurrent.getNumericValue()).isZero();
    }

    @Test
    void resetSpellSlotsDoesNothingWhenNoSpellSlotsExist() {
        when(attributeRepository.findBySheetIdOrderByPositionAsc(50L)).thenReturn(List.of());

        service.resetSpellSlots(sheet);

        verify(attributeRepository, never()).save(any(SheetAttribute.class));
    }
}
