package org.corpsman.rpg.service.character;

import org.corpsman.rpg.model.AttributeType;
import org.corpsman.rpg.model.CharacterSheet;
import org.corpsman.rpg.model.CustomAttribute;
import org.corpsman.rpg.model.Game;
import org.corpsman.rpg.model.GameSystem;
import org.corpsman.rpg.model.ListKind;
import org.corpsman.rpg.model.SheetAttribute;
import org.corpsman.rpg.repository.CustomAttributeRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Кастомная система: параметры задаёт ведущий (числовые, текстовые, списки).
 */
@Component
public class CustomCharacterSystem implements CharacterSystem {

    /** Префикс ключа кастомного параметра на листе. */
    public static final String KEY_PREFIX = "custom:";

    private final CustomAttributeRepository customAttributeRepository;

    public CustomCharacterSystem(CustomAttributeRepository customAttributeRepository) {
        this.customAttributeRepository = customAttributeRepository;
    }

    @Override
    public GameSystem system() {
        return GameSystem.CUSTOM;
    }

    @Override
    public List<AttributeSpec> template(Game game) {
        return customAttributeRepository.findByGameIdOrderByPositionAsc(game.getId()).stream()
                .map(this::toSpec)
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Integer> computedModifiers(CharacterSheet sheet, List<SheetAttribute> attrs) {
        return Map.of();
    }

    @Override
    public FieldConstraints constraints(String key) {
        Long id = idFromKey(key);
        if (id == null) {
            return FieldConstraints.empty();
        }
        return customAttributeRepository.findById(id)
                .map(this::toConstraints)
                .orElseGet(FieldConstraints::empty);
    }

    private Long idFromKey(String key) {
        if (key == null || !key.startsWith(KEY_PREFIX)) {
            return null;
        }
        try {
            return Long.valueOf(key.substring(KEY_PREFIX.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private FieldConstraints toConstraints(CustomAttribute a) {
        List<String> options = a.getType() == AttributeType.TEXT ? a.getOptions() : List.of();
        return new FieldConstraints(a.getDefaultValue(), a.getDefaultText(), a.getMinValue(),
                a.getMaxValue(), a.stepOrDefault(), options, a.isManualInputAllowed());
    }

    private AttributeSpec toSpec(CustomAttribute a) {
        ListKind listKind = a.getType() == AttributeType.LIST ? a.listKindOrDefault() : null;
        return new AttributeSpec(KEY_PREFIX + a.getId(), a.getName(), a.getType(), 400 + a.getPosition(),
                null, listKind);
    }
}