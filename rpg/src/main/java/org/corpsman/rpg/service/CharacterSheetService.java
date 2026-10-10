package org.corpsman.rpg.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.corpsman.auth.model.UserRole;
import org.corpsman.auth.repository.UserRepository;
import org.corpsman.common.exception.BusinessException;
import org.corpsman.common.exception.ErrorCode;
import org.corpsman.common.service.FileStorageService;
import org.corpsman.rpg.dto.AttributeViewDto;
import org.corpsman.rpg.dto.CharacterModifierDto;
import org.corpsman.rpg.dto.CharacterSheetViewDto;
import org.corpsman.rpg.dto.MoneyDto;
import org.corpsman.rpg.dto.MoneyEditDto;
import org.corpsman.rpg.dto.MoneyKindDto;
import org.corpsman.rpg.dto.SheetAttributeForm;
import org.corpsman.rpg.dto.SheetForm;
import org.corpsman.rpg.dto.SpellGroupDto;
import org.corpsman.rpg.dto.SpellListItemDto;
import org.corpsman.rpg.dto.SpellSlotDto;
import org.corpsman.rpg.dto.SpellSlotEditDto;
import org.corpsman.rpg.model.ApplicationStatus;
import org.corpsman.rpg.model.AttributeType;
import org.corpsman.rpg.model.CharacterSheet;
import org.corpsman.rpg.model.Game;
import org.corpsman.rpg.model.GameApplication;
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
import org.corpsman.rpg.service.character.FieldConstraints;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Сервис листов персонажей: создание по шаблону ролевой системы, обновление
 * параметров, рендер с расчётными модификаторами (D&amp;D) и видимостью отдельных
 * параметров.
 */
@Service
@RequiredArgsConstructor
public class CharacterSheetService {

    /** Подпись пункта «Другое» в списке с ручным вводом. */
    public static final String OTHER_OPTION_LABEL = "Другое";

    public static final String KEY_INVENTORY = "inventory";

    public static final String KEY_SPELLS = "spells";

    private static final int INVENTORY_POSITION = 540;

    /** Позиция блока денег: между инвентарём и ячейками заклинаний. */
    private static final int MONEY_POSITION = 545;

    private static final Map<String, String> LIST_NAMES = Map.of(
            KEY_INVENTORY, "Инвентарь",
            KEY_SPELLS, "Заклинания",
            "features", "Черты",
            "abilities", "Умения");

    private final CharacterSheetRepository sheetRepository;

    private final SheetAttributeRepository attributeRepository;

    private final GameApplicationRepository applicationRepository;

    private final GameAccessService accessService;

    private final CharacterSystemRegistry systemRegistry;

    private final FileStorageService fileStorageService;

    private final UserRepository userRepository;

    private final GameRepository gameRepository;

    private final ObjectMapper objectMapper;

    private static final TypeReference<List<ListItem>> LIST_ITEM_LIST = new TypeReference<>() {
    };

    @Transactional
    public CharacterSheet create(Game game, Long ownerId, boolean npc, SheetForm form, MultipartFile avatarFile) {
        CharacterSheet sheet = new CharacterSheet();
        sheet.setGameId(game.getId());
        sheet.setOwnerId(ownerId);
        sheet.setName(form.getName().trim());
        sheet.setKventa(trimToNull(form.getKventa()));
        sheet.setNpc(npc);
        sheet = sheetRepository.save(sheet);
        if (avatarFile != null && !avatarFile.isEmpty()) {
            sheet.setAvatarPath(fileStorageService.store(avatarFile));
            sheetRepository.save(sheet);
        }
        upsertAttributes(game, sheet, form.getAttributes());
        return sheet;
    }

    @Transactional
    public CharacterSheet update(Game game, CharacterSheet sheet, SheetForm form, MultipartFile avatarFile) {
        sheet.setName(form.getName().trim());
        sheet.setKventa(trimToNull(form.getKventa()));
        if (avatarFile != null && !avatarFile.isEmpty()) {
            sheet.setAvatarPath(fileStorageService.store(avatarFile));
        }
        sheetRepository.save(sheet);
        upsertAttributes(game, sheet, form.getAttributes());
        return sheet;
    }

    /**
     * Формирует параметры листа по шаблону системы + значения из формы.
     */
    private void upsertAttributes(Game game, CharacterSheet sheet, List<SheetAttributeForm> forms) {
        Map<String, SheetAttributeForm> formByKey = forms == null ? Map.of()
                : forms.stream().filter(f -> f.getKey() != null).collect(Collectors.toMap(
                        SheetAttributeForm::getKey, Function.identity(), (a, b) -> a));
        CharacterSystem system = systemRegistry.get(game.getSystem());

        List<AttributeSpec> specs = new ArrayList<>();
        specs.add(new AttributeSpec(KEY_INVENTORY, "Инвентарь", AttributeType.LIST, INVENTORY_POSITION, null, ListKind.SIMPLE));
        specs.addAll(system.template(game));

        Map<String, SheetAttribute> existing = attributeRepository.findBySheetIdOrderByPositionAsc(sheet.getId())
                .stream().collect(Collectors.toMap(SheetAttribute::getKey, Function.identity(), (a, b) -> a));

        for (AttributeSpec spec : specs) {
            SheetAttribute attr = existing.remove(spec.key());
            if (attr == null) {
                attr = new SheetAttribute();
                attr.setSheetId(sheet.getId());
                attr.setKey(spec.key());
                attr.setName(spec.name());
                attr.setType(spec.type());
                attr.setPosition(spec.order());
                applyListKind(attr, spec);
                if (spec.defaultValue() != null) {
                    attr.setNumericValue(spec.defaultValue());
                }
            } else {
                attr.setName(spec.name());
                attr.setType(spec.type());
                attr.setPosition(spec.order());
                applyListKind(attr, spec);
            }
            refreshListKind(attr, system, game);
            SheetAttributeForm submitted = formByKey.get(spec.key());
            applyDefault(attr, spec, system, submitted);
            validate(attr, spec, system, submitted);
            applyForm(attr, submitted);
            attributeRepository.save(attr);
        }
        // Существующие параметры, отсутствующие в шаблоне (например, ключи удалённых
        // кастомных параметров), сохраняем как есть.
        existing.values().forEach(attributeRepository::save);
    }

    /**
     * Проставляет тип списка из шаблона системы, не затирая уже сохранённый
     * сложный список (чтобы не потерять заголовки и описания).
     */
    private void applyListKind(SheetAttribute attr, AttributeSpec spec) {
        if (attr.getType() != AttributeType.LIST) {
            return;
        }
        Map<String, ListKind> specKinds = new HashMap<>();
        specKinds.put(attr.getKey(), spec.listKindOrDefault());
        attr.setListKind(effectiveListKind(attr.getKey(), attr.getListKind(), attr.getListValues(), specKinds));
    }

    private void applyForm(SheetAttribute attr, SheetAttributeForm form) {
        if (form == null) {
            return;
        }
        switch (attr.getType()) {
            case NUMERIC -> attr.setNumericValue(form.getNumericValue());
            case TEXT -> attr.setTextValue(form.getTextValue());
            case LIST -> {
                ListKind kind = resolveListKind(attr, form);
                attr.setListKind(kind);
                attr.setListValues(kind == ListKind.SIMPLE
                        ? writeSimpleList(readSimpleItemsFromForm(form))
                        : writeListItems(readComplexItemsFromForm(form)));
            }
        }
        if (form.getFlags() != null) {
            attr.setFlags(form.getFlags());
        }
    }

    /**
     * Тип списка для сохранения. Уже сохранённый сложный список (в том числе
     * старый JSON-массив объектов) не превращается в простой, иначе данные
     * инвентаря или черт потеряли бы заголовки и описания.
     */
    private ListKind resolveListKind(SheetAttribute attr, SheetAttributeForm form) {
        if (attr.getListKind() == ListKind.COMPLEX || looksComplex(attr.getListValues())) {
            return ListKind.COMPLEX;
        }
        return form.getListKind() == null ? ListKind.SIMPLE : form.getListKind();
    }

    /**
     * Пересчитывает вид списка перед сохранением: система главнее значения,
     * пришедшего из формы.
     */
    private void refreshListKind(SheetAttribute attr, CharacterSystem system, Game game) {
        if (attr.getType() != AttributeType.LIST) {
            return;
        }
        attr.setListKind(effectiveListKind(attr.getKey(), attr.getListKind(), attr.getListValues(),
                specListKinds(system, game)));
    }

    /**
     * true, если JSON-массив содержит объекты (а не строки) — признак
     * сложного списка в старых данных.
     */
    private boolean looksComplex(String json) {
        if (json == null || json.isBlank()) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            return root != null && root.isArray() && root.size() > 0 && root.get(0).isObject();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Элементы «простого» списка из формы: {@code listText} (по строке на
     * элемент) либо строки из {@code listJson}.
     */
    private List<String> readSimpleItemsFromForm(SheetAttributeForm form) {
        if (form.getListText() != null && !form.getListText().isBlank()) {
            return splitLines(form.getListText());
        }
        List<String> result = new ArrayList<>();
        for (ListItem item : readListItems(form.getListJson())) {
            if (item.getHeader() != null && !item.getHeader().isBlank()) {
                result.add(item.getHeader().trim());
            }
        }
        return result;
    }

    /**
     * Элементы «сложного» списка из формы: {@code listJson}. Пустое описание
     * приводится к пустой строке, отсутствующий уровень — к 1, уровень
     * ограничивается диапазоном 1–9, пустые заголовки отбрасываются.
     */
    private List<ListItem> readComplexItemsFromForm(SheetAttributeForm form) {
        List<ListItem> items = new ArrayList<>();
        for (ListItem raw : readListItems(form.getListJson())) {
            if (raw == null || raw.getHeader() == null || raw.getHeader().isBlank()) {
                continue;
            }
            items.add(new ListItem(
                    raw.getHeader().trim(),
                    raw.getDescription() == null ? "" : raw.getDescription().trim(),
                    normalizeLevel(raw.getLevel())));
        }
        return items;
    }

    private Integer normalizeLevel(Integer level) {
        if (level == null || level < 1) {
            return 1;
        }
        return Math.min(level, 9);
    }

    private List<String> splitLines(String text) {
        return text.lines().map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    /**
     * Рендер листа для просмотра: фильтр по видимости каждого атрибута и роли,
     * расчётные модификаторы заполняются ролевой системой.
     */
    @Transactional(readOnly = true)
    public CharacterSheetViewDto render(Game game, CharacterSheet sheet, UserRole viewerRole, Long viewerId) {
        CharacterSheetViewDto dto = new CharacterSheetViewDto();
        dto.setId(sheet.getId());
        dto.setGameId(sheet.getGameId());
        dto.setOwnerId(sheet.getOwnerId());
        dto.setOwnerName(userRepository.findById(sheet.getOwnerId()).map(u -> u.getUsername()).orElse("—"));
        dto.setName(sheet.getName());
        dto.setAvatarPath(sheet.getAvatarPath());
        dto.setKventa(sheet.getKventa());
        dto.setNpc(sheet.isNpc());

        CharacterSystem system = systemRegistry.get(game.getSystem());
        boolean isMaster = accessService.isMaster(game, viewerId);
        boolean isOwner = sheet.getOwnerId().equals(viewerId);
        // Бинарная видимость из настроек игры: либо все, имеющие доступ к игре,
        // видят все атрибуты, либо посторонние — только имя и квенту (без атрибутов).
        boolean seeAll = isMaster || isOwner || game.isAttributesVisible();
        Map<String, Integer> canonicalPos = new HashMap<>();
        for (AttributeSpec spec : system.template(game)) {
            canonicalPos.put(spec.key(), spec.order());
        }
        // Инвентарь добавляется сервисом вне шаблона системы — приводим к
        // канонической позиции (540), чтобы у старых листов он не сталкивался
        // с блоками списков (черты/умения/заклинания).
        canonicalPos.put(KEY_INVENTORY, 540);

        List<SheetAttribute> attrs = attributeRepository.findBySheetIdOrderByPositionAsc(sheet.getId());
        Map<String, Integer> modifiers = system.computedModifiers(sheet, attrs);
        Map<String, ListKind> specKinds = specListKinds(system, game);

        List<AttributeViewDto> views = new ArrayList<>();
        List<CharacterModifierDto> modifierList = new ArrayList<>();
        for (SheetAttribute a : attrs) {
            if (!seeAll) {
                break;
            }
            // Деньги выводятся отдельным блоком — в общем списке параметров их нет.
            if (DndCharacterSystem.isMoneyKey(a.getKey())) {
                continue;
            }
            AttributeViewDto view = toView(a, system, modifiers.get(a.getKey()), specKinds);
            // Инвентарь, как и ячейки, остаётся в списке параметров, но
            // выводится отдельным подразделом — ссылка на него для шаблона.
            if (KEY_INVENTORY.equals(a.getKey())) {
                dto.setInventory(view);
            }
            Integer pos = canonicalPos.get(a.getKey());
            if (pos != null) {
                view.setPosition(pos);
            }
            views.add(view);
            if (view.getModifier() != null && isCheckKey(a.getKey())) {
                CharacterModifierDto m = new CharacterModifierDto();
                m.setKey(a.getKey());
                m.setValue(view.getModifier());
                m.setLabel(view.getName() + " " + sign(view.getModifier()));
                modifierList.add(m);
            }
        }
        views.sort(Comparator.comparingInt(AttributeViewDto::getPosition));
        markGroups(views);
        dto.setAttributes(views);
        dto.setModifiers(modifierList);
        dto.setSpellSlots(buildSpellSlots(attrs));
        // Деньги — только в системе D&D: у остальных блока «Деньги» на листе нет.
        if (game.getSystem() == GameSystem.DND) {
            dto.setMoney(buildMoney(attrs));
            dto.setMoneyKinds(buildMoneyKinds(dto.getMoney()));
        }
        dto.setSpellGroups(buildSpellGroups(views));
        return dto;
    }

    /**
     * Группировка заклинаний по уровню (по возрастанию) для блока «Заклинания».
     * У каждого элемента сохраняется его индекс в исходном списке — он нужен
     * для удаления элемента из режима просмотра.
     */
    private List<SpellGroupDto> buildSpellGroups(List<AttributeViewDto> views) {
        List<SpellGroupDto> groups = new ArrayList<>();
        Map<Integer, SpellGroupDto> byLevel = new LinkedHashMap<>();
        for (AttributeViewDto v : views) {
            if (!KEY_SPELLS.equals(v.getKey()) || v.getListItems() == null) {
                continue;
            }
            List<ListItem> items = v.getListItems();
            for (int i = 0; i < items.size(); i++) {
                ListItem item = items.get(i);
                int level = normalizeLevel(item.getLevel());
                SpellListItemDto dto = new SpellListItemDto();
                dto.setIndex(i);
                dto.setHeader(item.getHeader());
                dto.setDescription(item.getDescription() == null ? "" : item.getDescription());
                dto.setLevel(level);
                byLevel.computeIfAbsent(level, k -> {
                    SpellGroupDto g = new SpellGroupDto();
                    g.setLevel(k);
                    return g;
                }).getItems().add(dto);
            }
        }
        byLevel.values().stream()
                .sorted(Comparator.comparingInt(SpellGroupDto::getLevel))
                .forEach(groups::add);
        return groups;
    }

    /**
     * Ячейки заклинаний одним списком по возрастанию уровня. Уровни, у которых
     * и текущее, и максимальное значение равны нулю, скрываются.
     */
    private List<SpellSlotDto> buildSpellSlots(List<SheetAttribute> attrs) {
        Map<Integer, int[]> byLevel = new TreeMap<>();
        for (SheetAttribute a : attrs) {
            String key = a.getKey();
            if (key == null) {
                continue;
            }
            int level;
            boolean current;
            if (key.startsWith(DndCharacterSystem.PREFIX_SPELL_SLOT_MAX)) {
                level = parseSpellSlotLevel(key, DndCharacterSystem.PREFIX_SPELL_SLOT_MAX);
                current = false;
            } else if (key.startsWith(DndCharacterSystem.PREFIX_SPELL_SLOT_CURRENT)) {
                level = parseSpellSlotLevel(key, DndCharacterSystem.PREFIX_SPELL_SLOT_CURRENT);
                current = true;
            } else {
                continue;
            }
            if (level < 1 || level > 9) {
                continue;
            }
            int[] pair = byLevel.computeIfAbsent(level, k -> new int[2]);
            pair[current ? 1 : 0] = nonNegative(a.getNumericValue());
        }
        List<SpellSlotDto> result = new ArrayList<>();
        for (Map.Entry<Integer, int[]> e : byLevel.entrySet()) {
            int max = e.getValue()[0];
            int current = e.getValue()[1];
            if (max == 0 && current == 0) {
                continue;
            }
            SpellSlotDto dto = new SpellSlotDto();
            dto.setLevel(e.getKey());
            dto.setMax(max);
            // Текущее может превышать максимум; в этом случае показываем предупреждение.
            dto.setCurrent(current);
            dto.setOverLimit(current > max);
            result.add(dto);
        }
        return result;
    }

    /**
     * Отрицательные значения ячеек недопустимы — приводим к нулю.
     */
    private int nonNegative(Integer value) {
        return value == null || value < 0 ? 0 : value;
    }

    /**
     * Тип списка для отображения: сохранённый в атрибуте, иначе выведенный
     * из фактического JSON (для данных, созданных до появления list_kind).
     */
    private ListKind resolveStoredListKind(SheetAttribute attr, Map<String, ListKind> specKindByKey) {
        return effectiveListKind(attr.getKey(), attr.getListKind(), attr.getListValues(), specKindByKey);
    }

    /**
     * Вид списка по единому правилу — одинаковому при чтении и при записи,
     * иначе значения разъезжаются.
     * <p>
     * Ролевая система — источник истины: она объявляет вид списка один раз, и
     * он действует для всех листов. Сохранённое значение — только запасной
     * вариант, причём «сложный» никогда не понижается до «простого», чтобы не
     * потерять заголовки и описания. Это же правило чинит листы, созданные до
     * появления колонки {@code list_kind}: миграция проставила им 'SIMPLE',
     * и без этого умения, черты и заклинания выглядели бы простыми списками.
     */
    private ListKind effectiveListKind(String key, ListKind stored, String json,
                                       Map<String, ListKind> specKindByKey) {
        ListKind fromSpec = specKindByKey.get(key);
        if (stored == ListKind.COMPLEX || fromSpec == ListKind.COMPLEX) {
            return ListKind.COMPLEX;
        }
        if (fromSpec != null) {
            return fromSpec;
        }
        // Шаблон вида не задал: угадываем по формату JSON (признак надёжен
        // только для непустого списка).
        return looksComplex(json) ? ListKind.COMPLEX : ListKind.SIMPLE;
    }

    /**
     * Вид списка по ключу из шаблона ролевой системы.
     */
    private Map<String, ListKind> specListKinds(CharacterSystem system, Game game) {
        Map<String, ListKind> kinds = new HashMap<>();
        kinds.put(KEY_INVENTORY, ListKind.SIMPLE);
        for (AttributeSpec spec : system.template(game)) {
            if (spec.type() == AttributeType.LIST) {
                kinds.put(spec.key(), spec.listKindOrDefault());
            }
        }
        return kinds;
    }

    /**
     * Признак того, что параметр — ячейка заклинаний. Такие параметры
     * рендерятся отдельным блоком «Ячейки», а не в общем списке.
     */
    public static boolean isSpellSlotKey(String key) {
        if (key == null) {
            return false;
        }
        return parseSpellSlotLevel(key, DndCharacterSystem.PREFIX_SPELL_SLOT_MAX) > 0
                || parseSpellSlotLevel(key, DndCharacterSystem.PREFIX_SPELL_SLOT_CURRENT) > 0;
    }

    private static int parseSpellSlotLevel(String key, String prefix) {
        try {
            return Integer.parseInt(key.substring(prefix.length()));
        } catch (RuntimeException e) {
            return -1;
        }
    }

    /**
     * Рендер листа для редактирования владельцем/ведущим: все параметры шаблона,
     * включая ещё не созданные (например, новые кастомные) — как пустые поля.
     */
    @Transactional(readOnly = true)
    public CharacterSheetViewDto renderForEdit(Game game, CharacterSheet sheet) {
        CharacterSheetViewDto dto = new CharacterSheetViewDto();
        dto.setId(sheet.getId());
        dto.setGameId(sheet.getGameId());
        dto.setOwnerId(sheet.getOwnerId());
        dto.setName(sheet.getName());
        dto.setAvatarPath(sheet.getAvatarPath());
        dto.setKventa(sheet.getKventa());
        dto.setNpc(sheet.isNpc());

        CharacterSystem system = systemRegistry.get(game.getSystem());
        Map<String, SheetAttribute> existing = attributeRepository.findBySheetIdOrderByPositionAsc(sheet.getId())
                .stream().collect(Collectors.toMap(SheetAttribute::getKey, Function.identity(), (a, b) -> a));

        List<AttributeSpec> specs = new ArrayList<>();
        specs.add(new AttributeSpec(KEY_INVENTORY, "Инвентарь", AttributeType.LIST, INVENTORY_POSITION, null, ListKind.SIMPLE));
        specs.addAll(system.template(game));

        List<SheetAttribute> all = new ArrayList<>();
        for (AttributeSpec spec : specs) {
            SheetAttribute a = existing.get(spec.key());
            if (a == null) {
                a = new SheetAttribute();
                a.setKey(spec.key());
                a.setName(spec.name());
                a.setType(spec.type());
                a.setPosition(spec.order());
                a.setListKind(spec.type() == AttributeType.LIST ? spec.listKindOrDefault() : null);
                if (spec.defaultValue() != null) {
                    a.setNumericValue(spec.defaultValue());
                }
                existing.put(spec.key(), a);
            }
            all.add(a);
        }
        Map<String, Integer> modifiers = system.computedModifiers(sheet, all);
        Map<String, ListKind> specKinds = specListKinds(system, game);

        List<AttributeViewDto> views = new ArrayList<>();
        for (AttributeSpec spec : specs) {
            SheetAttribute a = existing.get(spec.key());
            AttributeViewDto view;
            if (a != null) {
                view = toView(a, system, modifiers.get(a.getKey()), specKinds);
            } else {
                view = new AttributeViewDto();
                view.setKey(spec.key());
                view.setName(spec.name());
                view.setType(spec.type());
                view.setFlagMax(system.flagMax(spec.key()));
                if (spec.type() == AttributeType.LIST) {
                    view.setListKind(spec.listKindOrDefault());
                }
                applyConstraints(system, spec.key(), view, spec.defaultValue());
            }
            // Порядок показа берём из актуального шаблона системы, а не из
            // сохранённых значений (после смены шаблона могли остаться старые).
            view.setPosition(spec.order());
            if (view.getNumericValue() == null && spec.defaultValue() != null) {
                view.setNumericValue(spec.defaultValue());
            }
            views.add(view);
        }
        views.sort(Comparator.comparingInt(AttributeViewDto::getPosition));
        markGroups(views);
        dto.setAttributes(views);
        dto.setSpellSlotEdits(buildSpellSlotEdits(views));
        dto.setMoneyEdits(buildMoneyEdits(views));
        dto.setInventoryAttrIndex(indexOfKey(views, KEY_INVENTORY));
        return dto;
    }

    private Integer indexOfKey(List<AttributeViewDto> views, String key) {
        for (int i = 0; i < views.size(); i++) {
            if (key.equals(views.get(i).getKey())) {
                return i;
            }
        }
        return null;
    }

    /**
     * Разряды монет для формы редактирования.
     * <p>
     * Количество монет ограничено снизу нулём, поэтому у каждого разряда своя
     * нижняя граница и шаг; обмен доступен между соседними разрядами по курсу
     * 1:10 — у младшего разряда («медные») младше ничего нет.
     */
    private List<MoneyEditDto> buildMoneyEdits(List<AttributeViewDto> views) {
        Map<String, Integer> indexByKey = new HashMap<>();
        Map<String, Integer> valueByKey = new HashMap<>();
        for (int i = 0; i < views.size(); i++) {
            String key = views.get(i).getKey();
            if (key == null || !DndCharacterSystem.isMoneyKey(key)) {
                continue;
            }
            indexByKey.put(key, i);
            valueByKey.put(key, nonNegative(views.get(i).getNumericValue()));
        }
        List<String> kinds = DndCharacterSystem.MONEY_KINDS;
        List<MoneyEditDto> result = new ArrayList<>();
        for (int i = 0; i < kinds.size(); i++) {
            String key = kinds.get(i);
            Integer index = indexByKey.get(key);
            if (index == null) {
                continue;
            }
            String lower = i + 1 < kinds.size() ? kinds.get(i + 1) : null;
            result.add(new MoneyEditDto(key,
                    DndCharacterSystem.moneyLabel(key),
                    moneyAbbr(key),
                    valueByKey.getOrDefault(key, 0),
                    index,
                    0,
                    1,
                    lower,
                    lower == null ? null : moneyAbbr(lower)));
        }
        return result;
    }

    /** Сокращение вида монет: зм, см, мм. */
    private String moneyAbbr(String key) {
        return switch (key) {
            case DndCharacterSystem.MONEY_GOLD -> "зм";
            case DndCharacterSystem.MONEY_SILVER -> "см";
            case DndCharacterSystem.MONEY_COPPER -> "мм";
            default -> key;
        };
    }

    /**
     * Значение по умолчанию из шаблона системы или ограничений параметра.
     * Заполняется, только если игрок ничего не прислал: иначе выбранное им
     * значение сохранялось бы перезаписано.
     */
    private void applyDefault(SheetAttribute attr, AttributeSpec spec, CharacterSystem system,
                              SheetAttributeForm submitted) {
        FieldConstraints constraints = orEmpty(system.constraints(spec.key()));
        if (submitted != null) {
            return;
        }
        if (attr.getType() == AttributeType.NUMERIC && attr.getNumericValue() == null) {
            Integer fallback = spec.defaultValue() != null ? spec.defaultValue() : constraints.defaultValue();
            if (fallback != null) {
                attr.setNumericValue(fallback);
            }
        }
        if (attr.getType() == AttributeType.TEXT
                && (attr.getTextValue() == null || attr.getTextValue().isBlank())
                && constraints.defaultText() != null) {
            attr.setTextValue(constraints.defaultText());
        }
    }

    /**
     * Серверная проверка значения по ограничениям кастомной системы: число вне
     * границ и текст не из списка при запрещённом ручном вводе отклоняются.
     */
    private void validate(SheetAttribute attr, AttributeSpec spec, CharacterSystem system,
                          SheetAttributeForm submitted) {
        if (submitted == null) {
            return;
        }
        FieldConstraints constraints = orEmpty(system.constraints(spec.key()));
        if (attr.getType() == AttributeType.NUMERIC && constraints.outOfRange(submitted.getNumericValue())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Значение «" + spec.name() + "» вне границ");
        }
        if (attr.getType() == AttributeType.TEXT && constraints.notInOptions(submitted.getTextValue())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "Значение «" + spec.name() + "» выберите из списка");
        }
    }

    /**
     * Система без ограничений (в том числе заглушка в тестах) — пустые
     * ограничения.
     */
    private FieldConstraints orEmpty(FieldConstraints constraints) {
        return constraints == null ? FieldConstraints.empty() : constraints;
    }

    /**
     * Пары «текущее / максимальное» по уровням для формы редактирования.
     * Показываются <b>все</b> уровни 1–9, включая те, где ячеек ещё нет:
     * иначе невозможно задать количество ячеек для нового уровня.
     */
    private List<SpellSlotEditDto> buildSpellSlotEdits(List<AttributeViewDto> views) {
        Map<Integer, int[]> indexes = new TreeMap<>();
        Map<Integer, int[]> values = new TreeMap<>();
        for (int i = 0; i < views.size(); i++) {
            String key = views.get(i).getKey();
            if (key == null) {
                continue;
            }
            int level;
            boolean current;
            if (key.startsWith(DndCharacterSystem.PREFIX_SPELL_SLOT_MAX)) {
                level = parseSpellSlotLevel(key, DndCharacterSystem.PREFIX_SPELL_SLOT_MAX);
                current = false;
            } else if (key.startsWith(DndCharacterSystem.PREFIX_SPELL_SLOT_CURRENT)) {
                level = parseSpellSlotLevel(key, DndCharacterSystem.PREFIX_SPELL_SLOT_CURRENT);
                current = true;
            } else {
                continue;
            }
            if (level < 1 || level > 9) {
                continue;
            }
            indexes.computeIfAbsent(level, k -> new int[2])[current ? 1 : 0] = i;
            values.computeIfAbsent(level, k -> new int[2])[current ? 1 : 0] =
                    nonNegative(views.get(i).getNumericValue());
        }
        List<SpellSlotEditDto> result = new ArrayList<>();
        for (int level = 1; level <= 9; level++) {
            int[] idx = indexes.get(level);
            int[] val = values.get(level);
            // Уровень без параметров в шаблоне системы выводить нечего.
            if (idx == null || idx[0] < 0 || idx[1] < 0) {
                continue;
            }
            SpellSlotEditDto dto = new SpellSlotEditDto();
            dto.setLevel(level);
            dto.setMaxAttrIndex(idx[0]);
            dto.setCurrentAttrIndex(idx[1]);
            dto.setMax(val[0]);
            // Текущее может превышать максимум (например, после восстановления
            // ячеек при понижении максимума) — ограничиваем только снизу.
            dto.setCurrent(val[1]);
            result.add(dto);
        }
        return result;
    }

    /**
     * Проставляет заголовки групп (основное/характеристики/спасброски/навыки/
     * кастомные/блоки списков) и подзаголовки (способность для навыков D&amp;D)
     * на первый параметр каждой подгруппы.
     */
    private void markGroups(List<AttributeViewDto> views) {
        String last = null;
        String lastSub = null;
        for (AttributeViewDto v : views) {
            String header = sectionHeader(v);
            if (header != null && !header.equals(last)) {
                v.setGroupHeader(header);
            }
            last = header;
            String sub = sectionSubHeader(v);
            if (sub != null && !sub.equals(lastSub)) {
                v.setSubHeader(sub);
            }
            lastSub = sub;
        }
    }

    private String sectionHeader(AttributeViewDto v) {
        int pos = v.getPosition();
        if (pos < 100) {
            return "Основное";
        }
        if (pos < 200) {
            return "Характеристики";
        }
        if (pos < 300) {
            return "Спасброски";
        }
        if (pos < 400) {
            return "Навыки";
        }
        if (pos < 500) {
            return "Параметры";
        }
        // Блоки списков (черты, умения, заклинания, инвентарь и др.) — отдельные блоки.
        if (isSpellSlotKey(v.getKey())) {
            // Ячейки заклинаний рендерятся отдельным блоком «Ячейки».
            return null;
        }
        return v.getName();
    }

    /**
     * Подзаголовок внутри группы: для навыков D&amp;D — название способности,
     * к которой относится навык. Меняется только при смене способности.
     */
    private String sectionSubHeader(AttributeViewDto v) {
        if (v.getPosition() >= 400 || v.getKey() == null || !v.getKey().startsWith("skill:")) {
            return null;
        }
        String ability = org.corpsman.rpg.service.character.DndCharacterSystem
                .abilityForSkill(v.getKey().substring("skill:".length()));
        return ability == null ? null : org.corpsman.rpg.service.character.DndCharacterSystem.abilityName(ability);
    }

    private boolean isCheckKey(String key) {
        return key.startsWith("ability:") || key.startsWith("save:") || key.startsWith("skill:");
    }

    private AttributeViewDto toView(SheetAttribute a, CharacterSystem system, Integer modifier) {
        return toView(a, system, modifier, Map.of());
    }

    /**
     * @param specKindByKey вид списка из шаблона системы — подставляется, когда в
     *                      атрибуте не задан (лист создан до появления list_kind)
     */
    private AttributeViewDto toView(SheetAttribute a, CharacterSystem system, Integer modifier,
                                    Map<String, ListKind> specKindByKey) {
        AttributeViewDto v = new AttributeViewDto();
        v.setKey(a.getKey());
        v.setId(a.getId());
        v.setName(a.getName());
        v.setType(a.getType());
        v.setNumericValue(a.getNumericValue());
        v.setTextValue(a.getTextValue());
        v.setListItems(readListItems(a.getListValues()));
        v.setListKind(resolveStoredListKind(a, specKindByKey));
        v.setSpellSlot(isSpellSlotKey(a.getKey()));
        v.setFlags(a.getFlags());
        v.setPosition(a.getPosition());
        v.setVisibility(a.getVisibility());
        v.setModifier(modifier);
        v.setFlagMax(system.flagMax(a.getKey()));
if (a.getKey().startsWith("skill:")) {
                v.setAbilityKey(org.corpsman.rpg.service.character.DndCharacterSystem
                        .abilityForSkill(a.getKey().substring("skill:".length())));
            } else if (a.getKey().startsWith("save:")) {
                v.setAbilityKey(a.getKey().substring("save:".length()));
            }
        applyConstraints(system, a.getKey(), v, null);
        return v;
    }

    /**
     * Переносит ограничения параметра в вид для формы.
     * <p>
     * Варианты выбора берутся из ограничений кастомной системы, а у фиксированных
     * систем — из шаблона. Пункт «Другое» показывается только вместе со
     * списком вариантов и только при разрешённом ручном вводе.
     */
    private void applyConstraints(CharacterSystem system, String key, AttributeViewDto view,
                                  Integer systemDefault) {
        FieldConstraints constraints = orEmpty(system.constraints(key));
        List<String> options = constraints.hasOptions()
                ? constraints.options() : system.optionsFor(key);
        if (options != null && !options.isEmpty()) {
            view.setOptions(options);
            view.setOtherOptionLabel(constraints.hasOptions()
                    ? (constraints.manualInputAllowed() ? OTHER_OPTION_LABEL : "")
                    : system.otherOptionLabel(key));
        }
        if (constraints.min() != null || constraints.max() != null || constraints.step() != null) {
            view.setMinValue(constraints.min());
            view.setMaxValue(constraints.max());
            view.setStep(constraints.stepOrDefault());
            view.setManualInputAllowed(constraints.manualInputAllowed());
        }
        // Значение по умолчанию: сначала система, затем ограничения.
        if (view.getNumericValue() == null) {
            Integer fallback = systemDefault != null ? systemDefault : constraints.defaultValue();
            if (fallback != null) {
                view.setNumericValue(fallback);
            }
        }
        if ((view.getTextValue() == null || view.getTextValue().isBlank())
                && constraints.defaultText() != null) {
            view.setTextValue(constraints.defaultText());
        }
    }

    /**
     * Принятые игроки игры (не NPC) с рендером по видимости.
     */
    @Transactional(readOnly = true)
    public List<CharacterSheetViewDto> playersFor(Game game, UserRole viewerRole, Long viewerId) {
        List<GameApplication> accepted = applicationRepository.findByGameIdAndStatusOrderByCreatedAtDesc(
                game.getId(), ApplicationStatus.ACCEPTED);
        return accepted.stream()
                .map(a -> sheetRepository.findById(a.getSheetId()).orElse(null))
                .filter(s -> s != null && !s.isNpc())
                .map(s -> render(game, s, viewerRole, viewerId))
                .toList();
    }

    /**
     * NPC игры.
     */
    @Transactional(readOnly = true)
    public List<CharacterSheetViewDto> npcsFor(Game game, UserRole viewerRole, Long viewerId) {
        return sheetRepository.findByGameIdAndNpcOrderByCreatedAtDesc(game.getId(), true).stream()
                .map(s -> render(game, s, viewerRole, viewerId))
                .toList();
    }

    /**
     * Лист игры (с проверкой принадлежности).
     */
    @Transactional(readOnly = true)
    public CharacterSheet requireSheet(Game game, Long sheetId) {
        CharacterSheet sheet = sheetRepository.findById(sheetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHEET_NOT_FOUND));
        if (!sheet.getGameId().equals(game.getId())) {
            throw new BusinessException(ErrorCode.SHEET_NOT_FOUND);
        }
        return sheet;
    }

    /**
     * Право редактировать лист: владелец или ведущий.
     */
    public void requireSheetEdit(Game game, CharacterSheet sheet, Long userId) {
        if (!accessService.isMaster(game, userId) && !sheet.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.SHEET_PERMISSION_DENIED);
        }
    }

    public Game resolveGame(Long gameId) {
        return gameRepository.findById(gameId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GAME_NOT_FOUND));
    }

    public java.util.Optional<CharacterSheet> resolveSheet(Long sheetId) {
        return sheetRepository.findById(sheetId);
    }

    /**
     * Лист пользователя, привязанный к его заявке в игре.
     */
    public java.util.Optional<CharacterSheet> resolveSheetByOwnerAndGame(Long userId, Long gameId) {
        if (userId == null) {
            return java.util.Optional.empty();
        }
        return applicationRepository.findByGameIdAndUserId(gameId, userId)
                .flatMap(a -> sheetRepository.findById(a.getSheetId()));
    }

    /**
     * Записывает «простой» список — JSON-массивом строк.
     */
    private String writeSimpleList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception e) {
            return "[]";
        }
    }

    /**
     * Читает элементы списка с заголовком, описанием и уровнем.
     * Поддерживает оба формата: простые строки (старый) и объекты
     * {"header":"...", "description":"...", "level":N}.
     */
    private List<ListItem> readListItems(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isArray()) {
                return List.of();
            }
            List<ListItem> items = new ArrayList<>();
            for (JsonNode node : root) {
                if (node.isTextual()) {
                    String text = node.asText().trim();
                    if (!text.isEmpty()) {
                        items.add(new ListItem(text, "", null));
                    }
                } else if (node.isObject()) {
                    String header = jsonText(node, "header");
                    if (header == null || header.isBlank()) {
                        continue;
                    }
                    String description = jsonText(node, "description");
                    Integer level = node.hasNonNull("level") ? node.get("level").asInt() : null;
                    items.add(new ListItem(header.trim(),
                            description == null ? "" : description.trim(), level));
                }
            }
            return items;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String jsonText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    /**
     * Записывает элементы списка с заголовком и описанием в JSON.
     */
    private String writeListItems(List<ListItem> items) {
        if (items == null || items.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(items);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private String sign(int v) {
        return v >= 0 ? "+" + v : String.valueOf(v);
    }

    /**
     * Добавление предмета в инвентарь из режима просмотра. Инвентарь — всегда
     * простой список, поэтому хранится JSON-массивом строк.
     */
    @Transactional
    public void addInventoryItem(CharacterSheet sheet, String header, String description) {
        if (header == null || header.isBlank()) {
            return;
        }
        SheetAttribute attr = attributeRepository.findBySheetIdAndKey(sheet.getId(), KEY_INVENTORY)
                .orElseGet(() -> newListAttribute(sheet, KEY_INVENTORY));
        List<String> items = new ArrayList<>(readSimpleList(attr.getListValues()));
        items.add(header.trim());
        attr.setListKind(ListKind.SIMPLE);
        attr.setListValues(writeSimpleList(items));
        attributeRepository.save(attr);
    }

    /**
     * Удаление предмета инвентаря по индексу в списке.
     */
    @Transactional
    public void removeInventoryItem(CharacterSheet sheet, int itemIndex) {
        SheetAttribute attr = attributeRepository.findBySheetIdAndKey(sheet.getId(), KEY_INVENTORY).orElse(null);
        if (attr == null) {
            return;
        }
        List<String> items = new ArrayList<>(readSimpleList(attr.getListValues()));
        if (itemIndex >= 0 && itemIndex < items.size()) {
            items.remove(itemIndex);
            attr.setListKind(ListKind.SIMPLE);
            attr.setListValues(writeSimpleList(items));
            attributeRepository.save(attr);
        }
    }

    /**
     * Читает список строк. Старые данные, сохранённые как массив объектов,
     * читаются по полю заголовка — так ничего не теряется при конвертации.
     */
    private List<String> readSimpleList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isArray()) {
                return List.of();
            }
            List<String> items = new ArrayList<>();
            for (JsonNode node : root) {
                if (node.isTextual()) {
                    String text = node.asText().trim();
                    if (!text.isEmpty()) {
                        items.add(text);
                    }
                } else if (node.isObject()) {
                    String header = jsonText(node, "header");
                    if (header != null && !header.isBlank()) {
                        items.add(header.trim());
                    }
                }
            }
            return items;
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Создаёт отсутствующий список листа (например, инвентарь у старого листа):
     * имя и позиция берутся из шаблона ролевой системы, иначе используется
     * безопасное значение по умолчанию.
     */
    /**
     * Параметр-монета создаётся числовым и получает позицию блока денег.
     */
    private SheetAttribute newMoneyAttribute(CharacterSheet sheet, String key) {
        SheetAttribute a = new SheetAttribute();
        a.setSheetId(sheet.getId());
        a.setKey(key);
        a.setType(AttributeType.NUMERIC);
        a.setName(DndCharacterSystem.moneyLabel(key));
        a.setPosition(MONEY_POSITION + DndCharacterSystem.MONEY_KINDS.indexOf(key));
        return a;
    }

    private SheetAttribute newListAttribute(CharacterSheet sheet, String attrKey) {
        SheetAttribute a = new SheetAttribute();
        a.setSheetId(sheet.getId());
        a.setKey(attrKey);
        a.setType(AttributeType.LIST);
        a.setName(LIST_NAMES.getOrDefault(attrKey, attrKey));
        a.setPosition(listPosition(sheet, attrKey));
        a.setListKind(ListKind.SIMPLE);
        return a;
    }

    private int listPosition(CharacterSheet sheet, String attrKey) {
        if (attrKey.equals(KEY_INVENTORY)) {
            return INVENTORY_POSITION;
        }
        Game game = sheet.getGameId() == null ? null : gameRepository.findById(sheet.getGameId()).orElse(null);
        if (game == null) {
            return INVENTORY_POSITION;
        }
        return systemRegistry.get(game.getSystem()).template(game).stream()
                .filter(spec -> spec.key().equals(attrKey))
                .mapToInt(AttributeSpec::order)
                .findFirst()
                .orElse(INVENTORY_POSITION);
    }

    @Transactional
    /**
     * Сумма монет листа по трём числовым параметрам.
     */
    private MoneyDto buildMoney(List<SheetAttribute> attrs) {
        Map<String, Integer> values = new HashMap<>();
        for (SheetAttribute attr : attrs) {
            if (DndCharacterSystem.isMoneyKey(attr.getKey()) && attr.getNumericValue() != null) {
                values.put(attr.getKey(), attr.getNumericValue());
            }
        }
        return new MoneyDto(
                values.getOrDefault(DndCharacterSystem.MONEY_GOLD, 0),
                values.getOrDefault(DndCharacterSystem.MONEY_SILVER, 0),
                values.getOrDefault(DndCharacterSystem.MONEY_COPPER, 0));
    }

    /**
     * Разряды монет для блока «Деньги»: от старшего к младшему, с ключами
     * соседей для кнопок обмена.
     */
    private List<MoneyKindDto> buildMoneyKinds(MoneyDto money) {
        List<String> keys = DndCharacterSystem.MONEY_KINDS;
        List<String> shorts = List.of("зм", "см", "мм");
        List<String> params = List.of("gold", "silver", "copper");
        List<Integer> values = List.of(money.gold(), money.silver(), money.copper());
        List<MoneyKindDto> kinds = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            String lower = i + 1 < keys.size() ? keys.get(i + 1) : null;
            String upper = i > 0 ? keys.get(i - 1) : null;
            kinds.add(new MoneyKindDto(keys.get(i), DndCharacterSystem.moneyLabel(keys.get(i)),
                    shorts.get(i), params.get(i), values.get(i), lower,
                    lower == null ? null : shorts.get(i + 1), upper));
        }
        return kinds;
    }

    /**
     * Кнопки увеличения и уменьшения: одно нажатие — одна монета выбранного
     * вида. Сумма не уходит в минус.
     */
    @Transactional
    public void changeMoney(CharacterSheet sheet, String kind, Integer delta) {
        kind = moneyKind(kind);
        if (kind == null || delta == null) {
            return;
        }
        ensureMoneyAttribute(sheet, kind);
        attributeRepository.addMoney(sheet.getId(), kind, delta);
    }

    /**
     * Создаёт параметр вида монет, если его ещё нет: атомарному обновлению нечего
     * менять, а лист без разрядов монет встречается у старых листов.
     */
    private void ensureMoneyAttribute(CharacterSheet sheet, String kind) {
        attributeRepository.findBySheetIdAndKey(sheet.getId(), kind)
                .orElseGet(() -> {
                    SheetAttribute attr = newMoneyAttribute(sheet, kind);
                    attr.setNumericValue(0);
                    return attributeRepository.save(attr);
                });
    }

    /**
     * Конвертация по курсу 1:10. Одно нажатие — один обмен: одна монета
     * старшего разряда превращается в десять младшего. Обратное направление
     * требует десяти монет младшего разряда и даёт одну монету старшего;
     * некратное десяти остаётся в младшем разряде.
     */
    @Transactional
    public void convertMoney(CharacterSheet sheet, String kind, String direction) {
        kind = moneyKind(kind);
        if (kind == null) {
            return;
        }
        List<String> kinds = DndCharacterSystem.MONEY_KINDS;
        int index = kinds.indexOf(kind);
        // Медь — младший разряд: обменять её не на что, поэтому у неё нет
        // пары «младше».
        String lower = index + 1 < kinds.size() ? kinds.get(index + 1) : null;
        boolean up = "up".equals(direction);
        // Вверх — из kind в десять монет младшего разряда, вниз — наоборот:
        // десять монет младшего разряда в одну монету kind.
        String source = up ? kind : lower;
        String target = up ? lower : kind;
        if (source == null || target == null) {
            return;
        }
        ensureMoneyAttribute(sheet, source);
        ensureMoneyAttribute(sheet, target);
        // Одно нажатие — один обмен: вверх одна монета, вниз десять монет.
        int spent = up ? 1 : DndCharacterSystem.MONEY_RATE;
        int gained = up ? DndCharacterSystem.MONEY_RATE : 1;
        // Списание выполняет сама база и только если монет хватает: две
        // одновременные кнопки не спишут больше, чем есть.
        if (attributeRepository.spendMoney(sheet.getId(), source, spent) == 0) {
            return;
        }
        attributeRepository.addMoney(sheet.getId(), target, gained);
    }

    /**
     * Принимает и полный ключ параметра («money:gold»), и короткое имя вида
     * монет («gold»), которым называется поле ручного ввода.
     * <p>
     * null и неизвестное имя — не вид монет: запрос без вида монет (например,
     * от клиента без JS или с неполной формой) ничего не меняет и не роняет
     * страницу. Список видов — {@code List.of(...)} и на {@code null} отвечает
     * исключением, поэтому проверка обязательна.
     */
    private String moneyKind(String kind) {
        if (kind == null || kind.isBlank()) {
            return null;
        }
        if (DndCharacterSystem.MONEY_KINDS.contains(kind)) {
            return kind;
        }
        String full = DndCharacterSystem.PREFIX_MONEY + kind;
        return DndCharacterSystem.MONEY_KINDS.contains(full) ? full : null;
    }

    public void resetSpellSlots(CharacterSheet sheet) {
        // Для каждого уровня (1-9) устанавливаем текущее = макс.
        List<SheetAttribute> attrs = attributeRepository.findBySheetIdOrderByPositionAsc(sheet.getId());
        Map<String, SheetAttribute> byKey = attrs.stream()
                .collect(Collectors.toMap(SheetAttribute::getKey, Function.identity(), (a, b) -> a));

        for (int level = 1; level <= 9; level++) {
            String maxKey = DndCharacterSystem.PREFIX_SPELL_SLOT_MAX + level;
            String currentKey = DndCharacterSystem.PREFIX_SPELL_SLOT_CURRENT + level;
            SheetAttribute maxAttr = byKey.get(maxKey);
            SheetAttribute currentAttr = byKey.get(currentKey);
            if (maxAttr != null && currentAttr != null) {
                Integer maxVal = maxAttr.getNumericValue();
                if (maxVal != null && maxVal > 0 && !maxVal.equals(currentAttr.getNumericValue())) {
                    currentAttr.setNumericValue(maxVal);
                    attributeRepository.save(currentAttr);
                }
            }
        }
    }
}