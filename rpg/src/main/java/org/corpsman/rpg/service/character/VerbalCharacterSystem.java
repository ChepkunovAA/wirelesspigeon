package org.corpsman.rpg.service.character;

import org.corpsman.rpg.model.CharacterSheet;
import org.corpsman.rpg.model.Game;
import org.corpsman.rpg.model.GameSystem;
import org.corpsman.rpg.model.SheetAttribute;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Словесная система: дополнительных параметров нет.
 */
@Component
public class VerbalCharacterSystem implements CharacterSystem {

    @Override
    public GameSystem system() {
        return GameSystem.VERBAL;
    }

    @Override
    public List<AttributeSpec> template(Game game) {
        return List.of();
    }

    @Override
    public Map<String, Integer> computedModifiers(CharacterSheet sheet, List<SheetAttribute> attrs) {
        return Map.of();
    }
}