package org.corpsman.rpg.service.character;

import org.corpsman.rpg.model.GameSystem;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Реестр ролевых систем. Добавление новой системы = новая реализация
 * {@link CharacterSystem}, зарегистрированная как Spring-бин.
 */
@Component
public class CharacterSystemRegistry {

    private final Map<GameSystem, CharacterSystem> systems = new EnumMap<>(GameSystem.class);

    public CharacterSystemRegistry(List<CharacterSystem> implementations) {
        for (CharacterSystem system : implementations) {
            systems.put(system.system(), system);
        }
    }

    /**
     * Возвращает стратегию по системе. Неизвестная система рассматривается как словесная.
     *
     * @param system ролевая система
     * @return стратегия
     */
    public CharacterSystem get(GameSystem system) {
        return systems.getOrDefault(system, systems.get(GameSystem.VERBAL));
    }
}