package org.corpsman.rpg.service;

import lombok.RequiredArgsConstructor;
import org.corpsman.auth.model.UserRole;
import org.corpsman.auth.repository.UserRepository;
import org.corpsman.common.exception.BusinessException;
import org.corpsman.common.exception.ErrorCode;
import org.corpsman.rpg.dto.GameDetailDto;
import org.corpsman.rpg.dto.GameForm;
import org.corpsman.rpg.dto.GameSummaryDto;
import org.corpsman.rpg.dto.RoomDto;
import org.corpsman.rpg.model.Game;
import org.corpsman.rpg.model.GameVisibility;
import org.corpsman.rpg.model.GameWhitelistEntry;
import org.corpsman.rpg.repository.GameRepository;
import org.corpsman.rpg.repository.GameWhitelistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Сервис игр: CRUD, список по видимости, белый список, сборка страницы игры.
 */
@Service
@RequiredArgsConstructor
public class GameService {

    private final GameRepository gameRepository;

    private final GameWhitelistRepository whitelistRepository;

    private final GameAccessService accessService;

    private final CharacterSheetService characterSheetService;

    private final ApplicationService applicationService;

    private final RoomService roomService;

    private final UserRepository userRepository;

    @Transactional
    public Game create(UserRole role, Long masterId, GameForm form) {
        Game game = new Game();
        game.setTitle(form.getTitle().trim());
        game.setDescription(form.getDescription().trim());
        game.setMasterId(masterId);
        game.setSystem(form.getSystem());
        game.setVisibility(resolveVisibility(role, form));
        game.setStatus(form.getStatus());
        game.setAttributesVisible(form.isAttributesVisible());
        Game saved = gameRepository.save(game);
        saveWhitelist(saved.getId(), form.getWhitelistItems());
        return saved;
    }

    @Transactional
    public Game update(Long masterId, Long gameId, GameForm form) {
        Game game = find(gameId);
        if (!accessService.isMaster(game, masterId)) {
            throw new BusinessException(ErrorCode.GAME_PERMISSION_DENIED);
        }
        game.setTitle(form.getTitle().trim());
        game.setDescription(form.getDescription().trim());
        game.setSystem(form.getSystem());
        game.setVisibility(resolveVisibility(userRepository.findById(masterId).map(u -> u.getRole()).orElse(null), form));
        game.setStatus(form.getStatus());
        game.setAttributesVisible(form.isAttributesVisible());
        Game saved = gameRepository.save(game);
        saveWhitelist(saved.getId(), form.getWhitelistItems());
        return saved;
    }

    /**
     * Видимость из формы: OPEN + «доступно гостям» ⇒ GUEST. Гость может создать
     * только «Гостевую» (GUEST) или «Закрытую» игру.
     */
    private GameVisibility resolveVisibility(UserRole role, GameForm form) {
        GameVisibility v = form.getVisibility() == null ? GameVisibility.OPEN : form.getVisibility();
        if (v == GameVisibility.OPEN && form.isGuestAccess()) {
            v = GameVisibility.GUEST;
        }
        if (role == UserRole.GUEST && v != GameVisibility.GUEST && v != GameVisibility.CLOSED) {
            v = GameVisibility.GUEST;
        }
        return v;
    }

    /**
     * Перезаписывает белый список игры (для закрытой видимости).
     */
    private void saveWhitelist(Long gameId, List<Long> userIds) {
        whitelistRepository.deleteByGameId(gameId);
        whitelistRepository.flush();
        if (userIds == null) {
            return;
        }
        for (Long userId : userIds) {
            if (userId != null && !whitelistRepository.existsByGameIdAndUserId(gameId, userId)) {
                GameWhitelistEntry entry = new GameWhitelistEntry();
                entry.setGameId(gameId);
                entry.setUserId(userId);
                whitelistRepository.save(entry);
            }
        }
    }

    @Transactional(readOnly = true)
    public Game find(Long gameId) {
        return gameRepository.findById(gameId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GAME_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<GameSummaryDto> summaryList(String search, Long viewerId, boolean viewerGuest) {
        return summaryList(search, viewerId, viewerGuest, false);
    }

    @Transactional(readOnly = true)
    public List<GameSummaryDto> summaryList(String search, Long viewerId, boolean viewerGuest, boolean anonymous) {
        return gameRepository.search(search == null ? "" : search.trim(), viewerId, viewerGuest).stream()
                .filter(g -> !anonymous || g.getVisibility() == GameVisibility.PUBLIC)
                .map(g -> {
                    GameSummaryDto dto = new GameSummaryDto();
                    dto.setId(g.getId());
                    dto.setTitle(g.getTitle());
                    dto.setDescription(g.getDescription());
                    dto.setMasterId(g.getMasterId());
                    dto.setMasterName(userRepository.findById(g.getMasterId()).map(u -> u.getUsername()).orElse("—"));
                    dto.setVisibility(g.getVisibility());
                    dto.setSystem(g.getSystem());
                    dto.setStatus(g.getStatus());
                    dto.setParticipantCount(applicationService.acceptedPlayersCount(g.getId()));
                    return dto;
                }).toList();
    }

    @Transactional(readOnly = true)
    public GameDetailDto detail(Game game, UserRole role, Long viewerId) {
        requireView(game, role, viewerId);
        boolean isMaster = accessService.isMaster(game, viewerId);

        GameDetailDto dto = new GameDetailDto();
        dto.setId(game.getId());
        dto.setTitle(game.getTitle());
        dto.setDescription(game.getDescription());
        dto.setMasterId(game.getMasterId());
        dto.setMasterName(userRepository.findById(game.getMasterId()).map(u -> u.getUsername()).orElse("—"));
        dto.setVisibility(game.getVisibility());
        dto.setSystem(game.getSystem());
        dto.setStatus(game.getStatus());
        dto.setMasterViewer(isMaster);
        if (viewerId != null && !isMaster) {
            dto.setViewerApplication(applicationService.statusFor(game, viewerId));
        }
        if (isMaster) {
            dto.setApplications(applicationService.applicationsFor(game));
            dto.setWhitelistNames(whitelistRepository.findByGameIdOrderByCreatedAtDesc(game.getId()).stream()
                    .map(w -> userRepository.findById(w.getUserId()).map(u -> u.getUsername()).orElse("—"))
                    .toList());
        }
        dto.setPlayers(characterSheetService.playersFor(game, role, viewerId));
        dto.setNpcs(characterSheetService.npcsFor(game, role, viewerId));
        List<RoomDto> rooms = roomService.roomsFor(game, role, viewerId);
        dto.setRooms(rooms);
        return dto;
    }

    /**
     * Требует права просмотра игры и возвращает её сущность.
     */
    @Transactional(readOnly = true)
    public Game requireVisible(Long gameId, UserRole role, Long viewerId) {
        Game game = find(gameId);
        requireView(game, role, viewerId);
        return game;
    }

    /**
     * Требует права ведущего игры.
     */
    @Transactional(readOnly = true)
    public Game requireMaster(Long gameId, Long userId) {
        Game game = find(gameId);
        if (!accessService.isMaster(game, userId)) {
            throw new BusinessException(ErrorCode.GAME_PERMISSION_DENIED);
        }
        return game;
    }

    public void requireView(Game game, UserRole role, Long viewerId) {
        accessService.requireView(game, role, viewerId);
    }

    @Transactional
    public void addWhitelist(Long masterId, Long gameId, Long userId) {
        Game game = requireMaster(gameId, masterId);
        if (!whitelistRepository.existsByGameIdAndUserId(game.getId(), userId)) {
            GameWhitelistEntry entry = new GameWhitelistEntry();
            entry.setGameId(game.getId());
            entry.setUserId(userId);
            whitelistRepository.save(entry);
        }
    }

    @Transactional
    public void removeWhitelist(Long masterId, Long gameId, Long userId) {
        Game game = requireMaster(gameId, masterId);
        whitelistRepository.deleteByGameIdAndUserId(game.getId(), userId);
    }

    @Transactional(readOnly = true)
    public List<Long> whitelistUserIds(Long gameId) {
        return whitelistRepository.findByGameIdOrderByCreatedAtDesc(gameId).stream()
                .map(GameWhitelistEntry::getUserId)
                .toList();
    }

    public boolean whitelisted(Game game, Long userId) {
        return userId != null && whitelistRepository.existsByGameIdAndUserId(game.getId(), userId);
    }
}