package org.corpsman.rpg.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.corpsman.common.exception.BusinessException;
import org.corpsman.common.exception.ErrorCode;
import org.corpsman.rpg.dto.DiceRollDto;
import org.corpsman.rpg.dto.DieRollForm;
import org.corpsman.rpg.dto.SavedRollDto;
import org.corpsman.rpg.dto.SavedRollForm;
import org.corpsman.rpg.model.DiceRoll;
import org.corpsman.rpg.model.DiceSize;
import org.corpsman.rpg.model.SavedRoll;
import org.corpsman.rpg.repository.SavedRollRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * Сервис бросков костей и сохранённых комбинаций.
 */
@Service
@RequiredArgsConstructor
public class DiceService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SavedRollRepository savedRollRepository;

    private final ObjectMapper objectMapper;

    private static final TypeReference<List<Integer>> INT_LIST = new TypeReference<>() {
    };

    /**
     * Выполняет бросок костей по форме.
     *
     * @param form форма броска
     * @return экземпляр броска с результатами (владелец сохраняет его в репозитории сам)
     */
    public DiceRoll roll(DieRollForm form) {
        DiceSize die = form.getDie();
        if (die == null || DiceSize.byFaces(die.getFaces()) == null) {
            throw new BusinessException(ErrorCode.INVALID_DICE);
        }
        int count = clampCount(form.getCount());
        int modifier = form.getModifier() != null ? form.getModifier() : 0;

        List<Integer> results = new ArrayList<>();
        int sum = modifier;
        for (int i = 0; i < count; i++) {
            int r = RANDOM.nextInt(die.getFaces()) + 1;
            results.add(r);
            sum += r;
        }
        DiceRoll roll = new DiceRoll();
        roll.setDieSize(die.getFaces());
        roll.setCount(count);
        roll.setModifier(modifier);
        roll.setComment(form.getComment());
        roll.setResults(writeList(results));
        roll.setTotal(sum);
        return roll;
    }

    public DiceRollDto toDto(DiceRoll roll) {
        DiceRollDto dto = new DiceRollDto();
        dto.setDie(DiceSize.byFaces(roll.getDieSize()));
        dto.setCount(roll.getCount());
        dto.setModifier(roll.getModifier());
        dto.setComment(roll.getComment());
        dto.setResults(readList(roll.getResults()));
        dto.setTotal(roll.getTotal());
        return dto;
    }

    @Transactional
    public SavedRoll saveCombination(Long userId, SavedRollForm form) {
        return saveCombination(userId, null, form);
    }

    @Transactional
    public SavedRoll saveCombination(Long userId, Long gameId, SavedRollForm form) {
        return saveCombination(userId, gameId, null, form);
    }

    /**
     * Сохраняет комбинацию как «Избранное». Повторное сохранение той же комбинации
     * (те же кубик/количество/модификатор/комментарий) идемпотентно: записи не
     * дублируются, возвращается уже существующая.
     *
     * @param sheetId лист персонажа для «избранных NPC» (null — личное «Избранное»)
     */
    @Transactional
    public SavedRoll saveCombination(Long userId, Long gameId, Long sheetId, SavedRollForm form) {
        DiceSize die = form.getDie();
        if (die == null || DiceSize.byFaces(die.getFaces()) == null) {
            throw new BusinessException(ErrorCode.INVALID_DICE);
        }
        int count = clampCount(form.getCount());
        int modifier = form.getModifier() != null ? form.getModifier() : 0;
        String comment = normalizeComment(form.getComment());
        List<SavedRoll> existing = savedRollRepository
                .findForUserGameAndSheetNullable(userId, gameId, sheetId);
        for (SavedRoll saved : existing) {
            if (saved.getDieSize() == die.getFaces()
                    && saved.getCount() == count
                    && saved.getModifier() == modifier
                    && java.util.Objects.equals(saved.getComment(), comment)) {
                return saved;
            }
        }
        SavedRoll saved = new SavedRoll();
        saved.setUserId(userId);
        saved.setGameId(gameId);
        saved.setSheetId(sheetId);
        saved.setDieSize(die.getFaces());
        saved.setCount(count);
        saved.setModifier(modifier);
        saved.setComment(comment);
        return savedRollRepository.save(saved);
    }

    @Transactional
    public void deleteCombination(Long userId, Long savedRollId) {
        SavedRoll saved = savedRollRepository.findById(savedRollId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SAVED_ROLL_NOT_FOUND));
        if (!saved.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }
        savedRollRepository.delete(saved);
    }

    /**
     * Удаляет из «Избранного» все комбинации типа (die/count/modifier/comment),
     * принадлежащие пользователю и относящиеся к данной игре (в т.ч. избранное
     * верхнего уровня и броски NPC). Идемпотентно.
     */
    @Transactional
    public void deleteCombinationsOfKind(Long userId, Long gameId, int dieFaces, int count, int modifier, String comment) {
        String norm = normalizeComment(comment);
        savedRollRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(s -> s.getDieSize() == dieFaces
                        && s.getCount() == count
                        && s.getModifier() == modifier
                        && java.util.Objects.equals(s.getComment(), norm)
                        && (s.getGameId() == null || gameId.equals(s.getGameId())))
                .forEach(savedRollRepository::delete);
    }

    @Transactional(readOnly = true)
    public List<SavedRollDto> savedRollsFor(Long userId) {
        return savedRollRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toSavedDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SavedRollDto> savedRollsFor(Long userId, Long gameId) {
        return savedRollRepository.findTopLevelForUserGame(userId, gameId).stream()
                .map(this::toSavedDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SavedRollDto> savedRollsFor(Long userId, Long gameId, Long sheetId) {
        return savedRollRepository.findForUserGameAndSheet(userId, gameId, sheetId).stream()
                .map(this::toSavedDto)
                .toList();
    }

    public SavedRollDto toSavedDto(SavedRoll saved) {
        SavedRollDto dto = new SavedRollDto();
        dto.setId(saved.getId());
        dto.setDie(DiceSize.byFaces(saved.getDieSize()));
        dto.setCount(saved.getCount());
        dto.setModifier(saved.getModifier());
        dto.setComment(saved.getComment());
        return dto;
    }

    private int clampCount(Integer count) {
        int c = count == null ? 1 : count;
        return Math.max(1, Math.min(c, 1000));
    }

    private String normalizeComment(String comment) {
        return comment == null || comment.isBlank() ? null : comment.trim();
    }

    private String writeList(List<Integer> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<Integer> readList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, INT_LIST);
        } catch (Exception e) {
            return List.of();
        }
    }
}