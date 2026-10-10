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
import org.corpsman.rpg.repository.CharacterSheetRepository;
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

    private final CharacterSheetRepository sheetRepository;

    private final GameAccessService accessService;

    private final UserRepository userRepository;

    private final GameNotificationPublisher notificationPublisher;

    @Transactional
    public GameApplication apply(Game game, Long userId, SheetForm form, MultipartFile avatarFile) {
        if (accessService.isMaster(game, userId)) {
            throw new BusinessException(ErrorCode.CANNOT_APPLY_SELF);
        }
        GameApplication app = applicationRepository.findByGameIdAndUserId(game.getId(), userId).orElse(null);
        if (app != null && (app.getStatus() == ApplicationStatus.PENDING || app.getStatus() == ApplicationStatus.ACCEPTED)) {
            throw new BusinessException(ErrorCode.APPLICATION_ALREADY_EXISTS);
        }
        // Отозванную, отклонённую заявку или уход из игры можно повторить:
        // строка заявки на игру одна, поэтому она переиспользуется.
        if (app != null) {
            CharacterSheet existing = sheetRepository.findById(app.getSheetId()).orElse(null);
            CharacterSheet sheet = existing != null
                    ? characterSheetService.update(game, existing, form, avatarFile)
                    : characterSheetService.create(game, userId, false, form, avatarFile);
            app.setSheetId(sheet.getId());
            app.setStatus(ApplicationStatus.PENDING);
            app.setReviewedAt(null);
            GameApplication reused = applicationRepository.save(app);
            notificationPublisher.applicationSubmitted(game, reused);
            return reused;
        }
        CharacterSheet sheet = characterSheetService.create(game, userId, false, form, avatarFile);
        GameApplication fresh = new GameApplication();
        fresh.setGameId(game.getId());
        fresh.setUserId(userId);
        fresh.setSheetId(sheet.getId());
        fresh.setStatus(ApplicationStatus.PENDING);
        GameApplication saved = applicationRepository.save(fresh);
        notificationPublisher.applicationSubmitted(game, saved);
        return saved;
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
        notificationPublisher.applicationReviewed(game, app, accept);
    }

    /**
     * Игрок отзывает свою заявку, пока она ещё не рассмотрена.
     * Лист персонажа сохраняется: его можно доработать и подать заявку заново.
     */
    @Transactional
    public void withdraw(Game game, Long appId, Long userId) {
        GameApplication app = applicationRepository.findById(appId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));
        if (!app.getGameId().equals(game.getId()) || !app.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.GAME_PERMISSION_DENIED);
        }
        if (app.getStatus() != ApplicationStatus.PENDING) {
            throw new BusinessException(ErrorCode.APPLICATION_NOT_FOUND, "Заявка уже рассмотрена");
        }
        app.setStatus(ApplicationStatus.WITHDRAWN);
        applicationRepository.save(app);
        notificationPublisher.applicationWithdrawn(game, app);
    }

    /**
     * Уход игрока из игры: лист персонажа удаляется, а заявка уходит вместе с ним
     * (ON DELETE CASCADE по rpg_applications.sheet_id). Посты в комнатах
     * сохраняются — ссылка на лист у них nullable.
     */
    @Transactional
    public void leave(Game game, Long userId) {
        GameApplication app = applicationRepository.findByGameIdAndUserId(game.getId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND, "Вы не участвуете в игре"));
        if (app.getStatus() != ApplicationStatus.ACCEPTED) {
            throw new BusinessException(ErrorCode.APPLICATION_NOT_FOUND, "Вы не участвуете в игре");
        }
        removeParticipant(app);
        notificationPublisher.playerLeft(game, app);
    }

    /**
     * Ведущий удаляет принятого игрока из игры тем же способом.
     */
    @Transactional
    public void removePlayer(Game game, Long sheetId, Long masterId) {
        GameApplication app = applicationRepository.findByGameIdAndUserId(game.getId(), sheetOwnerId(game, sheetId))
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_NOT_FOUND));
        if (app.getStatus() != ApplicationStatus.ACCEPTED) {
            throw new BusinessException(ErrorCode.APPLICATION_NOT_FOUND, "Игрок не участвует в игре");
        }
        removeParticipant(app);
        notificationPublisher.playerRemoved(game, app);
    }

    private Long sheetOwnerId(Game game, Long sheetId) {
        return sheetRepository.findById(sheetId)
                .filter(s -> s.getGameId().equals(game.getId()))
                .map(CharacterSheet::getOwnerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHEET_NOT_FOUND));
    }

    private void removeParticipant(GameApplication app) {
        if (app.getSheetId() != null) {
            sheetRepository.deleteById(app.getSheetId());
        }
    }

    @Transactional(readOnly = true)
    public String statusFor(Game game, Long userId) {
        return applicationRepository.findByGameIdAndUserId(game.getId(), userId)
                .map(a -> a.getStatus().name())
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public ApplicationStatus statusOf(Game game, Long userId) {
        return applicationRepository.findByGameIdAndUserId(game.getId(), userId)
                .map(GameApplication::getStatus)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public Long applicationIdFor(Game game, Long userId) {
        return applicationRepository.findByGameIdAndUserId(game.getId(), userId)
                .map(GameApplication::getId)
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

    /**
     * Идентификаторы игр, в которых пользователь принят как игрок.
     */
    @Transactional(readOnly = true)
    public java.util.Set<Long> acceptedGameIds(Long userId) {
        return applicationRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, ApplicationStatus.ACCEPTED)
                .stream()
                .map(GameApplication::getGameId)
                .collect(java.util.stream.Collectors.toSet());
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