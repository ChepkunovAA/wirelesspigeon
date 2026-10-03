package org.corpsman.rpg.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.corpsman.auth.model.UserRole;
import org.corpsman.auth.repository.UserRepository;
import org.corpsman.auth.security.SecurityUtils;
import org.corpsman.common.exception.BusinessException;
import org.corpsman.common.exception.ErrorCode;
import org.corpsman.rpg.dto.AttributeViewDto;
import org.corpsman.rpg.dto.CharacterSheetViewDto;
import org.corpsman.rpg.dto.SheetAttributeForm;
import org.corpsman.rpg.dto.SheetForm;
import org.corpsman.rpg.model.CharacterSheet;
import org.corpsman.rpg.model.Game;
import org.corpsman.rpg.model.GameVisibility;
import org.corpsman.rpg.service.ApplicationService;
import org.corpsman.rpg.service.CharacterSheetService;
import org.corpsman.rpg.service.GameService;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Заявки, листы персонажей, NPC и видимость отдельных атрибутов.
 */
@Controller
@RequestMapping("/rpg/games/{gameId}")
@RequiredArgsConstructor
public class CharacterSheetController {

    private final GameService gameService;

    private final ApplicationService applicationService;

    private final CharacterSheetService characterSheetService;

    private final UserRepository userRepository;

    private final ObjectMapper objectMapper;

    /**
     * Страница заявки: новый лист (создание) или редактирование своей заявки.
     */
    @GetMapping("/apply")
    public String applyForm(@PathVariable Long gameId, Model model) {
        Long uid = SecurityUtils.currentUserId();
        Game game = gameService.requireVisible(gameId, freshRole(uid), uid);
        CharacterSheet sheet = ownSheet(game, uid);
        CharacterSheetViewDto view = characterSheetService.renderForEdit(game, sheet != null ? sheet : newSheet(game, uid));
        model.addAttribute("game", game);
        model.addAttribute("sheet", view);
        model.addAttribute("sheetId", sheet == null ? null : sheet.getId());
        model.addAttribute("sheetForm", formFrom(view));
        model.addAttribute("npcMode", false);
        model.addAttribute("editMode", false);
        model.addAttribute("previewMode", false);
        return "rpg/apply";
    }

    @PostMapping("/apply")
    public String apply(@PathVariable Long gameId,
                        @Validated @ModelAttribute("sheetForm") SheetForm form,
                        BindingResult binding,
                        @RequestParam(value = "avatar", required = false) MultipartFile avatar,
                        Model model) {
        Long uid = SecurityUtils.currentUserId();
        Game game = gameService.requireVisible(gameId, freshRole(uid), uid);
        if (binding.hasErrors()) {
            CharacterSheet sheet = ownSheet(game, uid);
            model.addAttribute("game", game);
            model.addAttribute("sheet", characterSheetService.renderForEdit(game, sheet != null ? sheet : newSheet(game, uid)));
            model.addAttribute("sheetForm", form);
            model.addAttribute("npcMode", false);
            model.addAttribute("editMode", false);
            return "rpg/apply";
        }
        CharacterSheet existing = ownSheet(game, uid);
        if (existing != null) {
            characterSheetService.update(game, existing, form, avatar);
        } else if (applicationService.statusFor(game, uid) == null) {
            applicationService.apply(game, uid, form, avatar);
        }
        return "redirect:/rpg/games/" + gameId;
    }

    @PostMapping("/npc")
    public String createNpc(@PathVariable Long gameId,
                            @Validated @ModelAttribute("sheetForm") SheetForm form,
                            BindingResult binding,
                            @RequestParam(value = "avatar", required = false) MultipartFile avatar) {
        Long uid = SecurityUtils.currentUserId();
        Game game = gameService.requireMaster(gameId, uid);
        if (!binding.hasErrors()) {
            applicationService.createNpc(game, uid, form, avatar);
        }
        return "redirect:/rpg/games/" + gameId;
    }

    @GetMapping("/npc/new")
    public String npcForm(@PathVariable Long gameId, Model model) {
        Long uid = SecurityUtils.currentUserId();
        Game game = gameService.requireMaster(gameId, uid);
        CharacterSheet sheet = newSheet(game, uid);
        CharacterSheetViewDto view = characterSheetService.renderForEdit(game, sheet);
        view.setNpc(true);
        model.addAttribute("game", game);
        model.addAttribute("sheet", view);
        model.addAttribute("sheetForm", formFrom(view));
        model.addAttribute("npcMode", true);
        model.addAttribute("editMode", false);
        model.addAttribute("previewMode", false);
        return "rpg/apply";
    }

    @GetMapping("/characters/{sheetId}")
    public String viewSheet(@PathVariable Long gameId, @PathVariable Long sheetId, Model model) {
        Long uid = SecurityUtils.currentUserId();
        Game game = gameService.requireVisible(gameId, freshRole(uid), uid);
        if (uid == null && game.getVisibility() != GameVisibility.PUBLIC) {
            throw new BusinessException(ErrorCode.GAME_PERMISSION_DENIED);
        }
        CharacterSheet sheet = characterSheetService.requireSheet(game, sheetId);
        model.addAttribute("game", game);
        model.addAttribute("sheet", characterSheetService.render(game, sheet, freshRole(uid), uid));
        model.addAttribute("isMaster", game.getMasterId().equals(uid));
        model.addAttribute("isOwner", sheet.getOwnerId().equals(uid));
        return "rpg/character";
    }

    @GetMapping("/characters/{sheetId}/edit")
    public String editSheetForm(@PathVariable Long gameId, @PathVariable Long sheetId, Model model) {
        Long uid = SecurityUtils.currentUserId();
        Game game = gameService.requireVisible(gameId, freshRole(uid), uid);
        CharacterSheet sheet = characterSheetService.requireSheet(game, sheetId);
        characterSheetService.requireSheetEdit(game, sheet, uid);
        CharacterSheetViewDto view = characterSheetService.renderForEdit(game, sheet);
        model.addAttribute("game", game);
        model.addAttribute("sheetId", sheet.getId());
        model.addAttribute("sheet", view);
        model.addAttribute("sheetForm", formFrom(view));
        model.addAttribute("editMode", true);
        model.addAttribute("npcMode", false);
        model.addAttribute("previewMode", false);
        return "rpg/apply";
    }

    @PostMapping("/characters/{sheetId}/edit")
    public String updateSheet(@PathVariable Long gameId, @PathVariable Long sheetId,
                              @Validated @ModelAttribute("sheetForm") SheetForm form,
                              BindingResult binding,
                              @RequestParam(value = "avatar", required = false) MultipartFile avatar,
                              Model model) {
        Long uid = SecurityUtils.currentUserId();
        Game game = gameService.requireVisible(gameId, freshRole(uid), uid);
        CharacterSheet sheet = characterSheetService.requireSheet(game, sheetId);
        characterSheetService.requireSheetEdit(game, sheet, uid);
        if (!binding.hasErrors()) {
            characterSheetService.update(game, sheet, form, avatar);
            return "redirect:/rpg/games/" + gameId + "/characters/" + sheetId;
        }
        model.addAttribute("game", game);
        model.addAttribute("sheet", characterSheetService.renderForEdit(game, sheet));
        model.addAttribute("sheetForm", form);
        model.addAttribute("editMode", true);
        model.addAttribute("npcMode", false);
        model.addAttribute("previewMode", false);
        return "rpg/apply";
    }

    @PostMapping("/applications/{appId}/accept")
    public String accept(@PathVariable Long gameId, @PathVariable Long appId) {
        Game game = gameService.requireMaster(gameId, SecurityUtils.currentUserId());
        applicationService.review(game, appId, true, SecurityUtils.currentUserId());
        return "redirect:/rpg/games/" + gameId;
    }

    @PostMapping("/applications/{appId}/reject")
    public String reject(@PathVariable Long gameId, @PathVariable Long appId) {
        Game game = gameService.requireMaster(gameId, SecurityUtils.currentUserId());
        applicationService.review(game, appId, false, SecurityUtils.currentUserId());
        return "redirect:/rpg/games/" + gameId;
    }

    /**
     * Добавление предмета в инвентарь прямо из режима просмотра.
     * Инвентарь — простой список, поэтому передаётся одна строка.
     * Правка остальных списков возможна только в режиме редактирования.
     */
    @PostMapping("/characters/{sheetId}/inventory-add")
    public String addInventoryItem(@PathVariable Long gameId, @PathVariable Long sheetId,
                                   @RequestParam String itemHeader) {
        Long uid = SecurityUtils.currentUserId();
        Game game = gameService.requireVisible(gameId, freshRole(uid), uid);
        CharacterSheet sheet = characterSheetService.requireSheet(game, sheetId);
        characterSheetService.requireSheetEdit(game, sheet, uid);
        characterSheetService.addInventoryItem(sheet, itemHeader, null);
        return "redirect:/rpg/games/" + gameId + "/characters/" + sheetId;
    }

    /**
     * Удаление предмета инвентаря по индексу в списке.
     */
    @PostMapping("/characters/{sheetId}/inventory-remove")
    public String removeInventoryItem(@PathVariable Long gameId, @PathVariable Long sheetId,
                                      @RequestParam int itemIndex) {
        Long uid = SecurityUtils.currentUserId();
        Game game = gameService.requireVisible(gameId, freshRole(uid), uid);
        CharacterSheet sheet = characterSheetService.requireSheet(game, sheetId);
        characterSheetService.requireSheetEdit(game, sheet, uid);
        characterSheetService.removeInventoryItem(sheet, itemIndex);
        return "redirect:/rpg/games/" + gameId + "/characters/" + sheetId;
    }

    /**
     * Сброс ячеек заклинаний: текущее значение приравнивается к максимальному.
     */
    @PostMapping("/characters/{sheetId}/spell-slots-reset")
    public String resetSpellSlots(@PathVariable Long gameId, @PathVariable Long sheetId) {
        Long uid = SecurityUtils.currentUserId();
        Game game = gameService.requireVisible(gameId, freshRole(uid), uid);
        CharacterSheet sheet = characterSheetService.requireSheet(game, sheetId);
        characterSheetService.requireSheetEdit(game, sheet, uid);
        characterSheetService.resetSpellSlots(sheet);
        return "redirect:/rpg/games/" + gameId + "/characters/" + sheetId;
    }

    private CharacterSheet ownSheet(Game game, Long uid) {
        if (uid == null) {
            return null;
        }
        Optional<CharacterSheet> found = characterSheetService.resolveSheetByOwnerAndGame(uid, game.getId());
        return found.orElse(null);
    }

    private CharacterSheet newSheet(Game game, Long uid) {
        CharacterSheet sheet = new CharacterSheet();
        sheet.setGameId(game.getId());
        sheet.setOwnerId(uid);
        return sheet;
    }

    private SheetForm formFrom(CharacterSheetViewDto view) {
        SheetForm form = new SheetForm();
        form.setName(view.getName());
        form.setKventa(view.getKventa());
        List<SheetAttributeForm> attrs = new ArrayList<>();
        for (AttributeViewDto a : view.getAttributes()) {
            SheetAttributeForm f = new SheetAttributeForm();
            f.setKey(a.getKey());
            f.setName(a.getName());
            f.setType(a.getType());
            f.setNumericValue(a.getNumericValue());
            f.setTextValue(a.getTextValue());
            fillList(f, a);
            f.setFlags(a.getFlags());
            attrs.add(f);
        }
        form.setAttributes(attrs);
        return form;
    }

    /**
     * Элементы списка в виде JSON для скрытого поля формы редактирования.
     */
    /**
     * Элементы списка в виде значения формы редактирования: простой список
     * передаётся построчно в {@code listText}, сложный — JSON в {@code listJson}.
     */
    private void fillList(SheetAttributeForm f, AttributeViewDto a) {
        f.setListKind(a.getListKind());
        if (a.getListKind() == org.corpsman.rpg.model.ListKind.COMPLEX) {
            f.setListJson(toListJson(a));
            f.setListText(null);
        } else {
            f.setListText(a.getListItems() == null ? ""
                    : a.getListItems().stream()
                    .map(org.corpsman.rpg.model.ListItem::getHeader)
                    .filter(h -> h != null && !h.isBlank())
                    .collect(java.util.stream.Collectors.joining("\n")));
            f.setListJson("[]");
        }
    }

    private String toListJson(AttributeViewDto a) {
        if (a.getListItems() == null || a.getListItems().isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(a.getListItems());
        } catch (Exception e) {
            return "[]";
        }
    }

    private UserRole freshRole(Long uid) {
        if (uid == null) {
            return null;
        }
        return userRepository.findById(uid).map(u -> u.getRole()).orElse(null);
    }
}