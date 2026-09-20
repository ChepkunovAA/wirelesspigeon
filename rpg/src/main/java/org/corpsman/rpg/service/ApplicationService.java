package org.corpsman.rpg.service;

import lombok.RequiredArgsConstructor;
import org.corpsman.auth.model.UserRole;
import org.corpsman.auth.repository.UserRepository;
import org.corpsman.common.exception.BusinessException;
import org.corpsman.common.exception.ErrorCode;
import org.corpsman.rpg.dto.GameApplicationDto;
import org.corpsman.rpg.dto.SheetForm;
import org.corpsman.rpg.model.ApplicationStatus;
import org.corpsman.rpg.model.CharacterSheet;
import org.corpsman.rpg.model.Game;
import org.corpsman.rpg.model.GameApplication;
import org.corpsman.rpg.repository.GameApplicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Сервис заявок на участие в игре.
 * <p>
 * Заявка всегда привязана к листу персонажа. До принятия ведущим пользователь
 * не считается игроком. NPC ведущего создаются с авто-принятием.
 */
@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final GameApplicationRepository applicationRepository;

    private final CharacterSheetService characterSheetService;

    private final GameAccessService accessService;

    private final UserRepository userRepository;

    @Transactional
    public GameApplication apply(Game game, Long userId, SheetForm form, MultipartFile avatarFile) {
        if (accessService.isMaster(game, userId)) {
            throw new BusinessException(ErrorCode.CANNOT_APPLY_SELF);
        }
        if (applicationRepository.existsByGameIdAndUserId(game.getId(), userId)) {
            throw new BusinessException(ErrorCode.APPLICATION_ALREADY_EXISTS);
        }
        CharacterSheet sheet = characterSheetService.create(game, userId, false, form, avatarFile);
        GameApplication app = new GameApplication();
        app.setGameId(game.getId());
        app.setUserId(userId);
        app.setSheetId(sheet.getId());
        app.setStatus(ApplicationStatus.PENDING);
        return applicationRepository.save(app);
    }

    /**
     * NPC ведущего: лист по тем же правилам, без заявки — у ведущего может
     * быть сколько угодно NPC (заявок на участие он не подаёт).
     */
    @Transactional
    public CharacterSheet createNpc(Game game, Long masterId, SheetForm form, MultipartFile avatarFile) {
        return characterSheetService.create(game, masterId, true, form, avatarFile);
    }

    @Transactional
    public void review(Game game, Long appId, boolean accept, Long masterId) {
        if (!accessService.isMaster(game, masterId)) {
            throw new BusinessException(ErrorCode.GAME_PERMISSION_DENIED);
        }
        GameApplication app = applicationRepository.findById(appId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));
        if (!app.getGameId().equals(game.getId())) {
            throw new BusinessException(ErrorCode.APPLICATION_NOT_FOUND);
        }
        if (app.getStatus() != ApplicationStatus.PENDING) {
            throw new BusinessException(ErrorCode.APPLICATION_NOT_FOUND, "Заявка уже рассмотрена");
        }
        app.setStatus(accept ? ApplicationStatus.ACCEPTED : ApplicationStatus.REJECTED);
        app.setReviewedAt(LocalDateTime.now());
        applicationRepository.save(app);
    }

    @Transactional(readOnly = true)
    public String statusFor(Game game, Long userId) {
        return applicationRepository.findByGameIdAndUserId(game.getId(), userId)
                .map(a -> a.getStatus().name())
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<GameApplicationDto> applicationsFor(Game game) {
        // В списке заявок ведущему показываем только нерассмотренные:
        // принятые и отклонённые оттуда уходят.
        return applicationRepository.findByGameIdAndStatusOrderByCreatedAtDesc(game.getId(), ApplicationStatus.PENDING).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public int acceptedPlayersCount(Long gameId) {
        return applicationRepository.findByGameIdAndStatusOrderByCreatedAtDesc(gameId, ApplicationStatus.ACCEPTED).size();
    }

    private GameApplicationDto toDto(GameApplication app) {
        GameApplicationDto dto = new GameApplicationDto();
        dto.setId(app.getId());
        dto.setGameId(app.getGameId());
        dto.setUserId(app.getUserId());
        dto.setUsername(userRepository.findById(app.getUserId()).map(u -> u.getUsername()).orElse("—"));
        dto.setSheetId(app.getSheetId());
        dto.setSheetName(characterSheetService.resolveSheet(app.getSheetId()).map(s -> s.getName()).orElse("—"));
        dto.setStatus(app.getStatus());
        dto.setCreatedAt(app.getCreatedAt());
        dto.setReviewedAt(app.getReviewedAt());
        return dto;
    }
}