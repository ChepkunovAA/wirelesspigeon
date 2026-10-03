package org.corpsman.rpg.web;

import lombok.RequiredArgsConstructor;
import org.corpsman.auth.security.SecurityUtils;
import org.corpsman.rpg.model.AttributeType;
import org.corpsman.rpg.model.ListKind;
import org.corpsman.rpg.model.CustomAttribute;
import org.corpsman.rpg.model.Game;
import org.corpsman.rpg.repository.CustomAttributeRepository;
import org.corpsman.rpg.service.GameService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Настройка кастомных параметров анкеты для системы «Кастомная» (ведущему).
 */
@Controller
@RequestMapping("/rpg/games/{gameId}/attributes")
@RequiredArgsConstructor
public class CustomAttributeController {

    private final GameService gameService;

    private final CustomAttributeRepository customAttributeRepository;

    @GetMapping
    public String configure(@PathVariable Long gameId, Model model) {
        Game game = gameService.requireMaster(gameId, SecurityUtils.currentUserId());
        model.addAttribute("game", game);
        model.addAttribute("attributes", customAttributeRepository.findByGameIdOrderByPositionAsc(gameId));
        model.addAttribute("attributeTypes", AttributeType.values());
        return "rpg/custom-attributes";
    }

    @PostMapping
    public String add(@PathVariable Long gameId,
                      @RequestParam String name,
                      @RequestParam(value = "attributeType", required = false) AttributeType attributeType,
                      @RequestParam(value = "listKind", required = false) ListKind listKind) {
        gameService.requireMaster(gameId, SecurityUtils.currentUserId());
        if (name != null && !name.isBlank()) {
            int last = customAttributeRepository.findByGameIdOrderByPositionAsc(gameId).stream()
                    .mapToInt(CustomAttribute::getPosition).max().orElse(-1);
            CustomAttribute attr = new CustomAttribute();
            attr.setGameId(gameId);
            attr.setName(name.trim());
            attr.setType(attributeType == null ? AttributeType.TEXT : attributeType);
            // Вид списка задаётся только для параметров типа LIST.
            attr.setListKind(attr.getType() == AttributeType.LIST
                    ? ListKind.parse(listKind == null ? null : listKind.name()) : null);
            attr.setPosition(last + 1);
            customAttributeRepository.save(attr);
        }
        return "redirect:/rpg/games/" + gameId + "/attributes";
    }

    @PostMapping("/{attributeId}/delete")
    public String delete(@PathVariable Long gameId, @PathVariable Long attributeId) {
        gameService.requireMaster(gameId, SecurityUtils.currentUserId());
        customAttributeRepository.deleteById(attributeId);
        return "redirect:/rpg/games/" + gameId + "/attributes";
    }

    @PostMapping("/{attributeId}/move")
    public String move(@PathVariable Long gameId, @PathVariable Long attributeId, @RequestParam String direction) {
        gameService.requireMaster(gameId, SecurityUtils.currentUserId());
        List<CustomAttribute> attrs = new ArrayList<>(customAttributeRepository.findByGameIdOrderByPositionAsc(gameId));
        int i = -1;
        for (int k = 0; k < attrs.size(); k++) {
            if (attrs.get(k).getId().equals(attributeId)) {
                i = k;
                break;
            }
        }
        if (i >= 0) {
            int j = "down".equals(direction) ? i + 1 : i - 1;
            if (j >= 0 && j < attrs.size()) {
                Collections.swap(attrs, i, j);
                for (int k = 0; k < attrs.size(); k++) {
                    attrs.get(k).setPosition(k);
                }
                customAttributeRepository.saveAll(attrs);
            }
        }
        return "redirect:/rpg/games/" + gameId + "/attributes";
    }
}