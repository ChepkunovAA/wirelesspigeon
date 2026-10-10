package org.corpsman.rpg.service;

import lombok.RequiredArgsConstructor;
import org.corpsman.common.exception.BusinessException;
import org.corpsman.common.exception.ErrorCode;
import org.corpsman.rpg.dto.SystemAttributeDto;
import org.corpsman.rpg.dto.GameSystemDefinitionForm;
import org.corpsman.rpg.model.AttributeType;
import org.corpsman.rpg.model.GameSystemDefinition;
import org.corpsman.rpg.repository.GameSystemDefinitionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;
import java.util.Optional;

/**
 * Сервис управления библиотекой ролевых систем.
 */
@Service
@RequiredArgsConstructor
public class GameSystemLibraryService {

    private final GameSystemDefinitionRepository definitionRepository;

    /**
     * Получить все публичные системы + системы пользователя.
     */
    @Transactional(readOnly = true)
    public List<GameSystemDefinition> getAvailableSystems(Long userId) {
        return definitionRepository.findAvailableForUser(userId);
    }

    /**
     * Получить все публичные системы.
     */
    @Transactional(readOnly = true)
    public List<GameSystemDefinition> getPublicSystems() {
        return definitionRepository.findByIsPublicTrueOrderByNameAsc();
    }

    /**
     * Получить системы пользователя.
     */
    @Transactional(readOnly = true)
    public List<GameSystemDefinition> getUserSystems(Long userId) {
        return definitionRepository.findByAuthorIdOrderByCreatedAtDesc(userId);
    }

    /** Размер страницы библиотеки систем. */
    public static final int SYSTEMS_PAGE_SIZE = 10;

    /**
     * Страница публичных систем, чужих для зрителя.
     */
    @Transactional(readOnly = true)
    public Page<GameSystemDefinition> publicSystemsPage(Long userId, String search, int pageNumber) {
        return definitionRepository.findPublicForOthers(userId, normalize(search),
                PageRequest.of(Math.max(pageNumber, 0), SYSTEMS_PAGE_SIZE));
    }

    /**
     * Страница собственных систем автора.
     */
    @Transactional(readOnly = true)
    public Page<GameSystemDefinition> ownSystemsPage(Long userId, String search, int pageNumber) {
        if (userId == null) {
            return Page.empty();
        }
        return definitionRepository.findOwn(userId, normalize(search),
                PageRequest.of(Math.max(pageNumber, 0), SYSTEMS_PAGE_SIZE));
    }

    private String normalize(String search) {
        return search == null ? "" : search.trim();
    }

    /**
     * Найти систему по ID с проверкой доступа.
     */
    @Transactional(readOnly = true)
    public GameSystemDefinition getSystem(Long systemId, Long userId) {
        GameSystemDefinition system = definitionRepository.findById(systemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SYSTEM_DEFINITION_NOT_FOUND));
        
        if (!system.isPublic() && !system.getAuthorId().equals(userId)) {
            throw new BusinessException(ErrorCode.SYSTEM_DEFINITION_ACCESS_DENIED);
        }
        return system;
    }

    /**
     * Создать новую систему в библиотеке.
     */
    @Transactional
    public GameSystemDefinition createSystem(Long authorId, String name, String description,
                                             List<SystemAttributeDto> templateAttributes, boolean isPublic) {
        if (definitionRepository.existsByNameIgnoreCase(name)) {
            throw new BusinessException(ErrorCode.SYSTEM_DEFINITION_NAME_EXISTS);
        }
        normalizeTextKinds(templateAttributes);
        validateTemplateAttributes(templateAttributes);

        GameSystemDefinition system = new GameSystemDefinition();
        system.setName(name);
        system.setDescription(description);
        system.setAuthorId(authorId);
        system.setPublic(isPublic);
        system.setTemplateAttributes(writeTemplateAttributes(templateAttributes));

        return definitionRepository.save(system);
    }

    /**
     * Обновить систему (только автор).
     */
    @Transactional
    public GameSystemDefinition updateSystem(Long systemId, Long userId,
                                             String name, String description,
                                             List<SystemAttributeDto> templateAttributes,
                                             Boolean isPublic) {
        GameSystemDefinition system = getSystem(systemId, userId);
        
        if (!system.getAuthorId().equals(userId)) {
            throw new BusinessException(ErrorCode.SYSTEM_DEFINITION_ACCESS_DENIED);
        }

        if (name != null && !name.equals(system.getName())) {
            if (definitionRepository.existsByNameIgnoreCase(name)) {
                throw new BusinessException(ErrorCode.SYSTEM_DEFINITION_NAME_EXISTS);
            }
            system.setName(name);
        }
        
        if (description != null) {
            system.setDescription(description);
        }
        
        if (templateAttributes != null) {
            normalizeTextKinds(templateAttributes);
            validateTemplateAttributes(templateAttributes);
            system.setTemplateAttributes(writeTemplateAttributes(templateAttributes));
            system.setVersion(system.getVersion() + 1);
        } else if (name != null || description != null) {
            system.setVersion(system.getVersion() + 1);
        }

        if (isPublic != null) {
system.setPublic(isPublic);
        }

        return definitionRepository.save(system);
    }

    /**
     * Удалить систему (только автор).
     */
    @Transactional
    public void deleteSystem(Long systemId, Long userId) {
        GameSystemDefinition system = getSystem(systemId, userId);
        
        if (!system.getAuthorId().equals(userId)) {
            throw new BusinessException(ErrorCode.SYSTEM_DEFINITION_ACCESS_DENIED);
        }

        definitionRepository.delete(system);
    }

    /**
     * Форма создания системы, предзаполненная данными исходной (форк).
     * <p>
     * Копия не создаётся сразу: пользователь правит название и состав
     * параметров на форме и только потом сохраняет систему. Название
     * получает суффикс «(форк)», видимость — приватная.
     */
    @Transactional(readOnly = true)
    public GameSystemDefinitionForm buildForkForm(GameSystemDefinition source) {
        GameSystemDefinitionForm form = new GameSystemDefinitionForm();
        form.setName(uniqueForkName(source.getName()));
        form.setDescription(source.getDescription());
        form.setPublished(false);
        form.setTemplateAttributes(new ArrayList<>(source.getTemplateAttributeList()));
        return form;
    }

    /**
     * Название копии, не пересекающееся с уже существующими системами.
     */
    private String uniqueForkName(String originalName) {
        String base = originalName + " (форк)";
        String candidate = base;
        int counter = 1;
        while (definitionRepository.existsByNameIgnoreCase(candidate)) {
            candidate = base + " " + counter++;
        }
        return candidate;
    }

    /**
     * Типовая анкета системы: состав параметров листа, заданный автором.
     */
    @Transactional(readOnly = true)
    public List<SystemAttributeDto> getTemplateForGameCreation(Long systemId, Long userId) {
        return getSystem(systemId, userId).getTemplateAttributeList();
    }

    /**
     * Приводит вид текстового параметра к тому, что из него следует.
     * <ul>
     *   <li>«Свободный»: список вариантов неприменим, а поле обязано быть
     *       заполняемым — иначе игрок его не заполнит.</li>
     *   <li>«По выбору»: значение по умолчанию само становится вариантом.
     *       Автору показывается предупреждение, но отказ с ошибкой был бы
     *       неудобен: правильный список получается сам.</li>
     * </ul>
     */
    private void normalizeTextKinds(List<SystemAttributeDto> attributes) {
        for (SystemAttributeDto attr : attributes) {
            if (attr == null || attr.getType() != AttributeType.TEXT) {
                continue;
            }
            if (!attr.isSelectable()) {
                attr.setOptions(new ArrayList<>());
                attr.setTextMode(SystemAttributeDto.TextMode.FREE);
                attr.setManualInputAllowed(true);
                continue;
            }
            attr.setTextMode(SystemAttributeDto.TextMode.SELECT);
            String defaultText = attr.getDefaultText();
            if (defaultText == null || defaultText.isBlank()) {
                continue;
            }
            String value = defaultText.trim();
            List<String> options = new ArrayList<>(attr.optionsOrEmpty());
            if (!options.contains(value)) {
                options.add(value);
            }
            attr.setOptions(options);
            attr.setDefaultText(value);
        }
    }

    /**
     * Сериализует состав параметров, проставляя порядок по номеру строки формы.
     * <p>
     * Поля «Порядок» в интерфейсе нет: автор задаёт только последовательность
     * параметров, а позиции для разделов листа ({@code 0, 10, 20, …}) считает
     * сервер. Значение, присланное вручную, игнорируется.
     */
    private String writeTemplateAttributes(List<SystemAttributeDto> attributes) {
        List<SystemAttributeDto> normalized = new ArrayList<>(attributes);
        for (int i = 0; i < normalized.size(); i++) {
            SystemAttributeDto attr = normalized.get(i);
            attr.setPosition(i * 10);
            // Ограничения относятся только к своему типу: числовые границы у
            // текстового параметра и варианты у числового в анкету не попадают.
            if (attr.getType() != AttributeType.NUMERIC) {
                attr.setDefaultValue(null);
                attr.setMinValue(null);
                attr.setMaxValue(null);
                attr.setStepValue(null);
            }
            if (attr.getType() != AttributeType.TEXT) {
                attr.setDefaultText(null);
                attr.setOptions(new ArrayList<>());
            }
            // У списка нет значения параметра: флаг ручного ввода неприменим.
            if (attr.getType() == AttributeType.LIST) {
                attr.setManualInputAllowed(false);
            }
        }
        GameSystemDefinition holder = new GameSystemDefinition();
        holder.setTemplateAttributeList(normalized);
        return holder.getTemplateAttributes();
    }

    /**
     * Проверяет ограничения параметров анкеты кастомной системы.
     * <p>
     * Числовому полю нельзя задать противоречащие друг другу границы или шаг
     * меньше единицы, значение по умолчанию обязано быть в границах. Текстовому
     * полю без ручного ввода нужен хотя бы один предустановленный вариант:
     * выбирать будет не из чего.
     */
    private void validateTemplateAttributes(List<SystemAttributeDto> attributes) {
        for (SystemAttributeDto attr : attributes) {
            if (attr == null) {
                continue;
            }
            // Параметр без имени в анкете не нужен: игрок не поймёт, что заполнять.
            if (attr.getName() == null || attr.getName().isBlank()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "У параметра должно быть название");
            }
            if (attr.getType() == AttributeType.NUMERIC) {
                if (attr.getMinValue() != null && attr.getMaxValue() != null
                        && attr.getMinValue() > attr.getMaxValue()) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT,
                            "Нижняя граница больше верхней");
                }
                if (attr.getStepValue() != null && attr.getStepValue() < 1) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT, "Шаг меньше единицы");
                }
                if (attr.getDefaultValue() != null
                        && (attr.getMinValue() != null && attr.getDefaultValue() < attr.getMinValue()
                        || attr.getMaxValue() != null && attr.getDefaultValue() > attr.getMaxValue())) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT,
                            "Значение по умолчанию вне границ");
                }
            }
            if (attr.getType() == AttributeType.TEXT) {
                List<String> options = attr.optionsOrEmpty();
                if (!attr.isManualInputAllowed() && options.isEmpty()) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT,
                            "Без ручного ввода нужен хотя бы один вариант значения");
                }
            }
        }
    }
}
