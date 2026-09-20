package org.corpsman.rpg.service;

import com.fasterxml.jackson.core.type.TypeReference;
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
import org.corpsman.rpg.dto.SheetAttributeForm;
import org.corpsman.rpg.dto.SheetForm;
import org.corpsman.rpg.model.ApplicationStatus;
import org.corpsman.rpg.model.AttributeType;
import org.corpsman.rpg.model.CharacterSheet;
import org.corpsman.rpg.model.Game;
import org.corpsman.rpg.model.GameApplication;
import org.corpsman.rpg.model.SheetAttribute;
import org.corpsman.rpg.repository.CharacterSheetRepository;
import org.corpsman.rpg.repository.GameApplicationRepository;
import org.corpsman.rpg.repository.GameRepository;
import org.corpsman.rpg.repository.SheetAttributeRepository;
import org.corpsman.rpg.service.character.AttributeSpec;
import org.corpsman.rpg.service.character.CharacterSystem;
import org.corpsman.rpg.service.character.CharacterSystemRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    public static final String KEY_INVENTORY = "inventory";

    private final CharacterSheetRepository sheetRepository;

    private final SheetAttributeRepository attributeRepository;

    private final GameApplicationRepository applicationRepository;

    private final GameAccessService accessService;

    private final CharacterSystemRegistry systemRegistry;

    private final FileStorageService fileStorageService;

    private final UserRepository userRepository;

    private final GameRepository gameRepository;

    private final ObjectMapper objectMapper;

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
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
        specs.add(new AttributeSpec(KEY_INVENTORY, "Инвентарь", AttributeType.LIST, 540));
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
                if (spec.defaultValue() != null) {
                    attr.setNumericValue(spec.defaultValue());
                }
            } else {
                attr.setName(spec.name());
                attr.setType(spec.type());
                attr.setPosition(spec.order());
            }
            applyForm(attr, formByKey.get(spec.key()));
            attributeRepository.save(attr);
        }
        // Существующие параметры, отсутствующие в шаблоне (например, ключи удалённых
        // кастомных параметров), сохраняем как есть.
        existing.values().forEach(attributeRepository::save);
    }

    private void applyForm(SheetAttribute attr, SheetAttributeForm form) {
        if (form == null) {
            return;
        }
        switch (attr.getType()) {
            case NUMERIC -> attr.setNumericValue(form.getNumericValue());
            case TEXT -> attr.setTextValue(form.getTextValue());
            case LIST -> {
                if (form.getListText() != null) {
                    attr.setListValues(writeList(splitLines(form.getListText())));
                } else {
                    attr.setListValues(writeList(form.getListValues()));
                }
            }
        }
        if (form.getFlags() != null) {
            attr.setFlags(form.getFlags());
        }
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

        List<AttributeViewDto> views = new ArrayList<>();
        List<CharacterModifierDto> modifierList = new ArrayList<>();
        for (SheetAttribute a : attrs) {
            if (!seeAll) {
                break;
            }
            AttributeViewDto view = toView(a, system, modifiers.get(a.getKey()));
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
        return dto;
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
        specs.add(new AttributeSpec(KEY_INVENTORY, "Инвентарь", AttributeType.LIST, 540));
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
                if (spec.defaultValue() != null) {
                    a.setNumericValue(spec.defaultValue());
                }
                existing.put(spec.key(), a);
            }
            all.add(a);
        }
        Map<String, Integer> modifiers = system.computedModifiers(sheet, all);

        List<AttributeViewDto> views = new ArrayList<>();
        for (AttributeSpec spec : specs) {
            SheetAttribute a = existing.get(spec.key());
            AttributeViewDto view;
            if (a != null) {
                view = toView(a, system, modifiers.get(a.getKey()));
            } else {
                view = new AttributeViewDto();
                view.setKey(spec.key());
                view.setName(spec.name());
                view.setType(spec.type());
                view.setFlagMax(system.flagMax(spec.key()));
                List<String> options = system.optionsFor(spec.key());
                if (options != null && !options.isEmpty()) {
                    view.setOptions(options);
                    view.setOtherOptionLabel(system.otherOptionLabel(spec.key()));
                }
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
        return dto;
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
        AttributeViewDto v = new AttributeViewDto();
        v.setKey(a.getKey());
        v.setId(a.getId());
        v.setName(a.getName());
        v.setType(a.getType());
        v.setNumericValue(a.getNumericValue());
        v.setTextValue(a.getTextValue());
        v.setListValues(readList(a.getListValues()));
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
        List<String> options = system.optionsFor(a.getKey());
        if (options != null && !options.isEmpty()) {
            v.setOptions(options);
            v.setOtherOptionLabel(system.otherOptionLabel(a.getKey()));
        }
        return v;
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

    private String writeList(List<String> values) {
        if (values == null) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<String> readList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (Exception e) {
            return List.of();
        }
    }

    private String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private String sign(int v) {
        return v >= 0 ? "+" + v : String.valueOf(v);
    }
}