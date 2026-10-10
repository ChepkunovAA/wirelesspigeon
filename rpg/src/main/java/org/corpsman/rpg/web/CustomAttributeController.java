package org.corpsman.rpg.web;

import lombok.RequiredArgsConstructor;
import org.corpsman.common.exception.BusinessException;
import org.corpsman.common.exception.ErrorCode;
import org.corpsman.auth.security.SecurityUtils;
import org.corpsman.rpg.dto.SystemAttributeDto;
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
                      @RequestParam(value = "listKind", required = false) ListKind listKind,
                      @RequestParam(value = "defaultValue", required = false) Integer defaultValue,
                      @RequestParam(value = "minValue", required = false) Integer minValue,
                      @RequestParam(value = "maxValue", required = false) Integer maxValue,
                      @RequestParam(value = "stepValue", required = false) Integer stepValue,
                      @RequestParam(value = "defaultText", required = false) String defaultText,
                      @RequestParam(value = "optionsText", required = false) String optionsText,
                      @RequestParam(value = "textMode", required = false) SystemAttributeDto.TextMode textMode,
                      @RequestParam(value = "manualInputAllowed", required = false) boolean manualInputAllowed,
                      @RequestParam(value = "manualNumericAllowed", required = false) boolean manualNumericAllowed) {
        gameService.requireMaster(gameId, SecurityUtils.currentUserId());
        if (name != null && !name.isBlank()) {
            AttributeType type = attributeType == null ? AttributeType.TEXT : attributeType;
            // Вид текстового поля: у свободного список вариантов неприменим.
            // Вида нет — значит список вариантов не задан, и поле свободное.
            boolean selectable = type == AttributeType.TEXT
                    && textMode != SystemAttributeDto.TextMode.FREE;
            List<String> options = selectable ? parseOptions(optionsText) : List.of();
            if (type == AttributeType.TEXT && options.isEmpty()) {
                // Списка нет — поле свободное, и заполнить его можно только
                // руками. Иначе выбирать не из чего и вводить нечем.
                selectable = false;
            }
            boolean manual = type == AttributeType.TEXT
                    ? (!selectable || manualInputAllowed)
                    : (manualInputAllowed || manualNumericAllowed);
            String textDefault = defaultText;
            if (type == AttributeType.TEXT && selectable && defaultText != null && !defaultText.isBlank()) {
                // Значение по умолчанию само становится вариантом: иначе поле
                // стартовало бы со значения, которого нет в списке.
                String value = defaultText.trim();
                if (!options.contains(value)) {
                    List<String> extended = new ArrayList<>(options);
                    extended.add(value);
                    options = extended;
                }
                textDefault = value;
            }
            validate(type, defaultValue, minValue, maxValue, stepValue, options, textDefault, manual);
            int last = customAttributeRepository.findByGameIdOrderByPositionAsc(gameId).stream()
                    .mapToInt(CustomAttribute::getPosition).max().orElse(-1);
            CustomAttribute attr = new CustomAttribute();
            attr.setGameId(gameId);
            attr.setName(name.trim());
            attr.setType(type);
            // Вид списка задаётся только для параметров типа LIST.
            attr.setListKind(type == AttributeType.LIST
                    ? ListKind.parse(listKind == null ? null : listKind.name()) : null);
            applyConstraints(attr, type, defaultValue, minValue, maxValue, stepValue, options,
                    textDefault, manual);
            attr.setPosition(last + 1);
            customAttributeRepository.save(attr);
        }
        return "redirect:/rpg/games/" + gameId + "/attributes";
    }

    /**
     * Разбирает предустановленные значения: по строке на вариант, без пустых.
     */
    private List<String> parseOptions(String optionsText) {
        if (optionsText == null || optionsText.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(optionsText.split("\\R"))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    /**
     * Проверяет ограничения параметра при создании.
     * <p>
     * Числовому полю нельзя задать противоречащие друг другу границы или шаг
     * меньше единицы, значение по умолчанию обязано быть в границах. Текстовому
     * полю без ручного ввода нужен хотя бы один предустановленный вариант:
     * выбирать будет не из чего.
     */
    private void validate(AttributeType type, Integer defaultValue, Integer minValue, Integer maxValue,
                         Integer stepValue, List<String> options, String defaultText,
                         boolean manualInputAllowed) {
        if (type == AttributeType.NUMERIC) {
            if (minValue != null && maxValue != null && minValue > maxValue) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "Нижняя граница больше верхней");
            }
            if (stepValue != null && stepValue < 1) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "Шаг меньше единицы");
            }
            if (defaultValue != null) {
                if (minValue != null && defaultValue < minValue
                        || maxValue != null && defaultValue > maxValue) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT,
                            "Значение по умолчанию вне границ");
                }
            }
        }
        if (type == AttributeType.TEXT) {
            if (!manualInputAllowed && options.isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "Без ручного ввода нужен хотя бы один вариант значения");
            }
        }
    }

    private void applyConstraints(CustomAttribute attr, AttributeType type, Integer defaultValue,
                                  Integer minValue, Integer maxValue, Integer stepValue,
                                  List<String> options, String defaultText, boolean manualInputAllowed) {
        // У списка нет значения параметра — флаг ручного ввода неприменим.
        attr.setManualInputAllowed(type != AttributeType.LIST && manualInputAllowed);
        if (type == AttributeType.NUMERIC) {
            attr.setDefaultValue(defaultValue);
            attr.setMinValue(minValue);
            attr.setMaxValue(maxValue);
            attr.setStepValue(stepValue == null ? 1 : stepValue);
        } else if (type == AttributeType.TEXT) {
            String text = defaultText == null ? "" : defaultText.trim();
            // Пустое значение по умолчанию — это отсутствие значения, а не пустая
            // строка: иначе лист стартовал бы с пустым полем вместо незаполненного.
            // Без вариантов поле свободное (значение сохраняется как есть),
            // со списком значение по умолчанию уже дополняет его.
            attr.setDefaultText(text.isEmpty() || options.isEmpty() ? (text.isEmpty() ? null : text)
                    : (options.contains(text) ? text : null));
            attr.setOptions(options);
        }
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