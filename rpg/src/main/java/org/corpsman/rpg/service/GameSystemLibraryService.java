package org.corpsman.rpg.service;

import lombok.RequiredArgsConstructor;
import org.corpsman.common.exception.BusinessException;
import org.corpsman.common.exception.ErrorCode;
import org.corpsman.rpg.dto.SystemAttributeDto;
import org.corpsman.rpg.dto.GameSystemDefinitionForm;
import org.corpsman.rpg.model.GameSystemDefinition;
import org.corpsman.rpg.repository.GameSystemDefinitionRepository;
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
     * Сериализует состав параметров, проставляя порядок по номеру строки формы.
     * <p>
     * Поля «Порядок» в интерфейсе нет: автор задаёт только последовательность
     * параметров, а позиции для разделов листа ({@code 0, 10, 20, …}) считает
     * сервер. Значение, присланное вручную, игнорируется.
     */
    private String writeTemplateAttributes(List<SystemAttributeDto> attributes) {
        List<SystemAttributeDto> normalized = new ArrayList<>(attributes);
        for (int i = 0; i < normalized.size(); i++) {
            normalized.get(i).setPosition(i * 10);
        }
        GameSystemDefinition holder = new GameSystemDefinition();
        holder.setTemplateAttributeList(normalized);
        return holder.getTemplateAttributes();
    }
}
