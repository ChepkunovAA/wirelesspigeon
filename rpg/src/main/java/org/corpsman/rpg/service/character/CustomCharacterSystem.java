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

    private AttributeSpec toSpec(CustomAttribute a) {
        ListKind listKind = a.getType() == AttributeType.LIST ? a.listKindOrDefault() : null;
        return new AttributeSpec("custom:" + a.getId(), a.getName(), a.getType(), 400 + a.getPosition(),
                null, listKind);
    }
}