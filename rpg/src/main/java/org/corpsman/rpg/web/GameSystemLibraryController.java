package org.corpsman.rpg.web;

import lombok.RequiredArgsConstructor;
import org.corpsman.auth.model.UserRole;
import org.corpsman.auth.repository.UserRepository;
import org.corpsman.auth.security.SecurityUtils;
import org.corpsman.common.exception.BusinessException;
import org.corpsman.common.exception.ErrorCode;
import org.corpsman.rpg.dto.GameSystemDefinitionDto;
import org.corpsman.rpg.dto.GameSystemDefinitionForm;
import org.corpsman.rpg.model.GameSystemDefinition;
import org.corpsman.rpg.service.GameSystemLibraryService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import org.corpsman.rpg.dto.AttributeViewDto;
import org.corpsman.rpg.dto.CharacterSheetViewDto;
import org.corpsman.rpg.dto.SheetAttributeForm;
import org.corpsman.rpg.dto.SheetForm;
import org.corpsman.rpg.dto.SystemAttributeDto;
import org.corpsman.rpg.model.AttributeType;
import org.corpsman.rpg.model.Game;
import org.corpsman.rpg.model.GameSystem;
import org.corpsman.rpg.model.ListKind;
import org.corpsman.rpg.service.CharacterSheetService;

import java.util.ArrayList;
import java.util.List;

/**
 * Контроллер библиотеки ролевых систем.
 * <p>
 * Позволяет просматривать, создавать, редактировать и удалять кастомные системы.
 * При создании игры можно выбрать систему из библиотеки.
 */
@Controller
@RequestMapping("/rpg/systems")
@RequiredArgsConstructor
public class GameSystemLibraryController {

    private final GameSystemLibraryService libraryService;
    private final UserRepository userRepository;

    @GetMapping
    public String listSystems(@RequestParam(value = "search", required = false) String search,
                              @RequestParam(value = "page", required = false, defaultValue = "0") int page,
                              Model model) {
        Long uid = SecurityUtils.currentUserId();
        UserRole role = uid != null ? userRepository.findById(uid).map(u -> u.getRole()).orElse(null) : null;

        // Чужие публичные и собственные системы показываются отдельными
        // списками, у каждого своя пагинация.
        Page<GameSystemDefinition> mine = libraryService.ownSystemsPage(uid, search, page);
        Page<GameSystemDefinition> publicSystems = libraryService.publicSystemsPage(
                uid == null ? -1L : uid, search, page);

        model.addAttribute("mine", mine.getContent().stream().map(this::toDto).toList());
        model.addAttribute("publicSystems", publicSystems.getContent().stream().map(this::toDto).toList());
        model.addAttribute("minePage", mine);
        model.addAttribute("publicPage", publicSystems);
        model.addAttribute("search", search == null ? "" : search);
        model.addAttribute("currentUserRole", role);
        // Кнопку форка показываем любому вошедшему: сделать копию может кто угодно.
        model.addAttribute("currentUserId", uid);
        return "rpg/systems-list";
    }

    /**
     * Форма публикации новой системы.
     * <p>
     * С параметром {@code forkFrom} форма открывается уже заполненной данными
     * указанной системы: пользователь правит их и сохраняет как свою систему.
     */
    @GetMapping("/new")
    public String newSystemForm(@RequestParam(required = false) Long forkFrom, Model model) {
        Long uid = SecurityUtils.currentUserId();
        if (uid == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        GameSystemDefinitionForm form;
        String forkSourceName = null;
        if (forkFrom != null) {
            GameSystemDefinition source = libraryService.getSystem(forkFrom, uid);
            form = libraryService.buildForkForm(source);
            forkSourceName = source.getName();
        } else {
            form = new GameSystemDefinitionForm();
        }

        model.addAttribute("form", form);
        model.addAttribute("forkSourceName", forkSourceName);
        return "rpg/system-form";
    }

    @PostMapping("/new")
    public String createSystem(@Validated @ModelAttribute("form") GameSystemDefinitionForm form,
                               BindingResult binding,
                               Model model) {
        Long uid = SecurityUtils.currentUserId();
        if (uid == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        
        if (binding.hasErrors()) {
            return "rpg/system-form";
        }

        GameSystemDefinition system = libraryService.createSystem(
            uid,
            form.getName(),
            form.getDescription(),
            form.getTemplateAttributes(),
            form.isPublished()
        );

        return "redirect:/rpg/systems/" + system.getId();
    }

    @GetMapping("/{systemId}")
    public String viewSystem(@PathVariable Long systemId, Model model) {
        Long uid = SecurityUtils.currentUserId();
        Long effectiveUid = uid != null ? uid : -1L;
        GameSystemDefinition system = libraryService.getSystem(systemId, effectiveUid);
        
        model.addAttribute("system", toDto(system));
        model.addAttribute("isAuthor", system.getAuthorId().equals(uid));
        // Кнопку форка показываем любому вошедшему: сделать копию может кто угодно.
        model.addAttribute("currentUserId", uid);
        return "rpg/system-view";
    }

    @GetMapping("/{systemId}/edit")
    public String editSystemForm(@PathVariable Long systemId, Model model) {
        GameSystemDefinition system = libraryService.getSystem(systemId, SecurityUtils.currentUserId());
        
        if (!system.getAuthorId().equals(SecurityUtils.currentUserId())) {
            throw new BusinessException(ErrorCode.SYSTEM_DEFINITION_ACCESS_DENIED);
        }

        GameSystemDefinitionForm form = new GameSystemDefinitionForm();
        form.setName(system.getName());
        form.setDescription(system.getDescription());
        form.setPublished(system.isPublic());
        form.setTemplateAttributes(new java.util.ArrayList<>(system.getTemplateAttributeList()));
        
        model.addAttribute("system", toDto(system));
        model.addAttribute("form", form);
        return "rpg/system-form";
    }

    @PostMapping("/{systemId}/edit")
    public String updateSystem(@PathVariable Long systemId,
                               @Validated @ModelAttribute("form") GameSystemDefinitionForm form,
                               BindingResult binding) {
        if (binding.hasErrors()) {
            return "rpg/system-form";
        }

        libraryService.updateSystem(
            systemId,
            SecurityUtils.currentUserId(),
            form.getName(),
            form.getDescription(),
            form.getTemplateAttributes(),
            form.isPublished()
        );

        return "redirect:/rpg/systems/" + systemId;
    }

    /**
     * Режим создания персонажа по правилам этой системы.
     * <p>
     * Открывается любому пользователю, который смотрит библиотеку. Лист не
     * сохраняется: это предпросмотр анкеты. Состав параметров определяется
     * системой, к которой привязана игра (для CUSTOM — параметры, настроенные
     * ведущим игры).
     */
    @GetMapping("/{systemId}/character")
    public String characterPreview(@PathVariable Long systemId, Model model) {
        Long uid = SecurityUtils.currentUserId();
        GameSystemDefinition system = libraryService.getSystem(systemId, uid);

        Game game = new Game();
        game.setId(-1L);
        game.setTitle(system.getName());
        game.setSystem(GameSystem.CUSTOM);
        game.setDescription(null);

        CharacterSheetViewDto sheet = new CharacterSheetViewDto();
        sheet.setName("Пробный персонаж");
        sheet.setKventa(null);
        sheet.setNpc(false);

        List<AttributeViewDto> views = new ArrayList<>();
        List<SheetAttributeForm> attrForms = new ArrayList<>();

        // Состав параметров берётся из анкеты системы — так игрок видит то же,
        // что получит в игре на этой системе.
        List<SystemAttributeDto> template = system.getTemplateAttributeList();
        if (template.isEmpty()) {
            template = List.of(new SystemAttributeDto());
        }
        int position = 0;
        for (SystemAttributeDto spec : template) {
            AttributeViewDto v = new AttributeViewDto();
            SheetAttributeForm f = new SheetAttributeForm();
            String key;
            if (spec.getName() == null || spec.getName().isBlank()) {
                key = "inventory";
                v.setName("Инвентарь");
                v.setType(AttributeType.LIST);
                v.setListKind(ListKind.SIMPLE);
            } else {
                key = "custom-preview:" + position;
                v.setName(spec.getName());
                v.setType(spec.getType() == null ? AttributeType.TEXT : spec.getType());
                v.setListKind(v.getType() == AttributeType.LIST ? spec.listKindOrDefault() : null);
            }
            v.setKey(key);
            v.setPosition(400 + position * 10);
            v.setListItems(List.of());
            applyPreviewConstraints(v, spec);
            views.add(v);

            f.setKey(key);
            f.setName(v.getName());
            f.setType(v.getType());
            f.setListKind(v.getListKind());
            // Значения по умолчанию обязаны попасть и в форму: поля страницы
            // биндятся по sheetForm, поэтому без этого стрелки числового поля
            // считали бы пустое значение и ничего не меняли.
            f.setNumericValue(v.getNumericValue());
            f.setTextValue(v.getTextValue());
            f.setListText("");
            f.setListJson("[]");
            attrForms.add(f);
            position++;
        }
        if (position == 1 && (views.get(0).getKey().equals("inventory"))) {
            views.get(0).setPosition(540);
        }

        sheet.setAttributes(views);

        SheetForm form = new SheetForm();
        form.setName("Пробный персонаж");
        form.setAttributes(attrForms);

        model.addAttribute("game", game);
        model.addAttribute("sheet", sheet);
        model.addAttribute("sheetForm", form);
        model.addAttribute("sheetId", null);
        model.addAttribute("npcMode", false);
        model.addAttribute("editMode", false);
        model.addAttribute("previewMode", true);
        model.addAttribute("previewSystem", toDto(system));
        return "rpg/apply";
    }

    @PostMapping("/{systemId}/delete")
    public String deleteSystem(@PathVariable Long systemId) {
        libraryService.deleteSystem(systemId, SecurityUtils.currentUserId());
        return "redirect:/rpg/systems";
    }

    /**
     * Ограничения параметра в пробном листе — те же, что игрок увидит в игре:
     * границы и шаг числовому полю, варианты и значение по умолчанию — текстовому.
     */
    private void applyPreviewConstraints(AttributeViewDto view, SystemAttributeDto spec) {
        if (spec == null) {
            return;
        }
        view.setManualInputAllowed(spec.isManualInputAllowed());
        if (view.getType() == AttributeType.NUMERIC) {
            view.setMinValue(spec.getMinValue());
            view.setMaxValue(spec.getMaxValue());
            view.setStep(spec.stepOrDefault());
            view.setNumericValue(spec.getDefaultValue());
        } else if (view.getType() == AttributeType.TEXT) {
            List<String> options = spec.optionsOrEmpty();
            if (!options.isEmpty()) {
                view.setOptions(options);
                // Пункт «Другое» открывает ручной ввод. Без него поле с белым
                // списком в пробном листе не заполнить, если автор его разрешил.
                if (spec.isManualInputAllowed()) {
                    view.setOtherOptionLabel(CharacterSheetService.OTHER_OPTION_LABEL);
                }
            }
            String defaultText = spec.getDefaultText();
            if (defaultText != null && !defaultText.isBlank()) {
                view.setTextValue(defaultText);
            }
        }
    }

    private GameSystemDefinitionDto toDto(GameSystemDefinition system) {
        GameSystemDefinitionDto dto = new GameSystemDefinitionDto();
        dto.setId(system.getId());
        dto.setName(system.getName());
        dto.setDescription(system.getDescription());
        dto.setAuthorId(system.getAuthorId());
        dto.setPublic(system.isPublic());
        dto.setCreatedAt(system.getCreatedAt());
        dto.setTemplateAttributes(new java.util.ArrayList<>(system.getTemplateAttributeList()));
        return dto;
    }
}