package org.corpsman.rpg.service;

import org.corpsman.common.exception.BusinessException;
import org.corpsman.rpg.dto.SystemAttributeDto;
import org.corpsman.rpg.model.GameSystemDefinition;
import org.corpsman.rpg.model.AttributeType;
import org.corpsman.rpg.model.GameSystemDefinition;
import org.corpsman.rpg.repository.GameSystemDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ограничения параметров кастомной системы из библиотеки: числовой параметр
 * (значение по умолчанию, границы, шаг, ручной ввод) и текстовый (значение по
 * умолчанию, варианты, ручной ввод) — как задаёт автор при публикации системы.
 */
@ExtendWith(MockitoExtension.class)
class GameSystemLibraryConstraintsTest {

    @Mock
    private GameSystemDefinitionRepository definitionRepository;

    private GameSystemLibraryService service;

    @BeforeEach
    void setUp() {
        service = new GameSystemLibraryService(definitionRepository);
    }

    private SystemAttributeDto numeric() {
        SystemAttributeDto attr = new SystemAttributeDto();
        attr.setName("Мана");
        attr.setType(AttributeType.NUMERIC);
        attr.setDefaultValue(5);
        attr.setMinValue(0);
        attr.setMaxValue(30);
        attr.setStepValue(2);
        attr.setManualInputAllowed(true);
        return attr;
    }

    private SystemAttributeDto text() {
        SystemAttributeDto attr = new SystemAttributeDto();
        attr.setName("Мировоззрение");
        attr.setType(AttributeType.TEXT);
        attr.setDefaultText("Хаос");
        attr.setOptions(List.of("Хаос", "Закон", "Равновесие"));
        attr.setManualInputAllowed(true);
        return attr;
    }

    private List<SystemAttributeDto> savedAttributes() {
        ArgumentCaptor<GameSystemDefinition> captor = ArgumentCaptor.forClass(GameSystemDefinition.class);
        verify(definitionRepository).save(captor.capture());
        return captor.getValue().getTemplateAttributeList();
    }

    @Test
    void savesNumericConstraintsOfCustomSystem() {
        when(definitionRepository.existsByNameIgnoreCase("Магия")).thenReturn(false);
        when(definitionRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        service.createSystem(1L, "Магия", "Правила", new ArrayList<>(List.of(numeric())), true);

        List<SystemAttributeDto> saved = savedAttributes();
        assertThat(saved).hasSize(1);
        SystemAttributeDto attr = saved.get(0);
        assertThat(attr.getName()).isEqualTo("Мана");
        assertThat(attr.getType()).isEqualTo(AttributeType.NUMERIC);
        assertThat(attr.getDefaultValue()).isEqualTo(5);
        assertThat(attr.getMinValue()).isZero();
        assertThat(attr.getMaxValue()).isEqualTo(30);
        assertThat(attr.getStepValue()).isEqualTo(2);
        assertThat(attr.isManualInputAllowed()).isTrue();
    }

    @Test
    void savesTextConstraintsOfCustomSystem() {
        when(definitionRepository.existsByNameIgnoreCase("Мир")).thenReturn(false);
        when(definitionRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        service.createSystem(1L, "Мир", "Правила", new ArrayList<>(List.of(text())), true);

        SystemAttributeDto attr = savedAttributes().get(0);
        assertThat(attr.getDefaultText()).isEqualTo("Хаос");
        assertThat(attr.getOptions()).containsExactly("Хаос", "Закон", "Равновесие");
        assertThat(attr.isManualInputAllowed()).isTrue();
    }

    @Test
    void rejectsNumericBoundsInverted() {
        SystemAttributeDto attr = numeric();
        attr.setMinValue(10);
        attr.setMaxValue(5);

        assertThatThrownBy(() -> service.createSystem(1L, "Магия", "", new ArrayList<>(List.of(attr)), true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Нижняя граница больше верхней");
    }

    @Test
    void rejectsNumericStepBelowOne() {
        SystemAttributeDto attr = numeric();
        attr.setStepValue(0);

        assertThatThrownBy(() -> service.createSystem(1L, "Магия", "", new ArrayList<>(List.of(attr)), true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Шаг меньше единицы");
    }

    @Test
    void rejectsNumericDefaultOutsideBounds() {
        SystemAttributeDto attr = numeric();
        attr.setMaxValue(3);

        assertThatThrownBy(() -> service.createSystem(1L, "Магия", "", new ArrayList<>(List.of(attr)), true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Значение по умолчанию вне границ");
    }

    @Test
    void makesTextWithoutOptionsFillableInsteadOfRejectingIt() {
        // Раньше такое поле отклонялось: выбирать не из чего и вводить нечем.
        // Теперь ручной ввод включается принудительно — поле остаётся рабочим.
        SystemAttributeDto attr = text();
        attr.setOptions(List.of());
        attr.setManualInputAllowed(false);
        when(definitionRepository.existsByNameIgnoreCase("Мир")).thenReturn(false);
        when(definitionRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        service.createSystem(1L, "Мир", "", new ArrayList<>(List.of(attr)), true);

        SystemAttributeDto saved = savedAttributes().get(0);
        assertThat(saved.getOptions()).isEmpty();
        assertThat(saved.isManualInputAllowed()).isTrue();
    }

    @Test
    void addsDefaultTextToOptionsWhenSelectableAndMissing() {
        // Значение по умолчанию не из списка: автору показывается предупреждение,
        // но сохранить он должен иначе, чем ошибкой 400.
        SystemAttributeDto attr = text();
        attr.setOptions(List.of("Закон", "Равновесие"));
        attr.setTextMode(SystemAttributeDto.TextMode.SELECT);
        when(definitionRepository.existsByNameIgnoreCase("Мир")).thenReturn(false);
        when(definitionRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        service.createSystem(1L, "Мир", "Правила", new ArrayList<>(List.of(attr)), true);

        SystemAttributeDto saved = savedAttributes().get(0);
        assertThat(saved.getDefaultText()).isEqualTo("Хаос");
        assertThat(saved.getOptions()).containsExactly("Закон", "Равновесие", "Хаос");
        assertThat(saved.getTextMode()).isEqualTo(SystemAttributeDto.TextMode.SELECT);
    }

    @Test
    void keepsDefaultTextOutOfOptionsForFreeTextMode() {
        SystemAttributeDto attr = text();
        attr.setOptions(List.of());
        attr.setTextMode(SystemAttributeDto.TextMode.FREE);
        when(definitionRepository.existsByNameIgnoreCase("Мир")).thenReturn(false);
        when(definitionRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        service.createSystem(1L, "Мир", "Правила", new ArrayList<>(List.of(attr)), true);

        SystemAttributeDto saved = savedAttributes().get(0);
        assertThat(saved.getOptions()).isEmpty();
        assertThat(saved.getDefaultText()).isEqualTo("Хаос");
        // Свободное поле обязано оставаться заполняемым.
        assertThat(saved.isManualInputAllowed()).isTrue();
    }

    @Test
    void keepsTextModeAcrossSerialization() {
        // Подтип хранится, а не выводится из списка вариантов: иначе значение
        // поля «Вид» не переживало бы правку системы.
        SystemAttributeDto attr = text();
        attr.setTextMode(SystemAttributeDto.TextMode.SELECT);
        when(definitionRepository.existsByNameIgnoreCase("Мир")).thenReturn(false);
        when(definitionRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        service.createSystem(1L, "Мир", "Правила", new ArrayList<>(List.of(attr)), true);

        String json = jsonOf(attr);
        assertThat(json).contains("\"textMode\"");
        assertThat(json).contains("SELECT");

        GameSystemDefinition reloaded = new GameSystemDefinition();
        reloaded.setTemplateAttributes(json);
        assertThat(reloaded.getTemplateAttributeList().get(0).getTextMode())
                .isEqualTo(SystemAttributeDto.TextMode.SELECT);
    }

    private String jsonOf(SystemAttributeDto attr) {
        GameSystemDefinition holder = new GameSystemDefinition();
        holder.setTemplateAttributeList(new ArrayList<>(List.of(attr)));
        return holder.getTemplateAttributes();
    }

    @Test
    void updateReplacesConstraintsOfCustomSystem() {
        GameSystemDefinition system = new GameSystemDefinition();
        system.setId(7L);
        system.setAuthorId(1L);
        system.setName("Магия");
        system.setPublic(true);
        system.setVersion(0L);
        when(definitionRepository.findById(7L)).thenReturn(Optional.of(system));
        when(definitionRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        SystemAttributeDto attr = numeric();
        attr.setManualInputAllowed(false);
        service.updateSystem(7L, 1L, null, null, new ArrayList<>(List.of(attr)), null);

        SystemAttributeDto saved = savedAttributes().get(0);
        assertThat(saved.isManualInputAllowed()).isFalse();
        assertThat(saved.getMaxValue()).isEqualTo(30);
    }
}