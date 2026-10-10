package org.corpsman.rpg.service;

import lombok.RequiredArgsConstructor;
import org.corpsman.auth.model.UserRole;
import org.corpsman.common.exception.BusinessException;
import org.corpsman.common.exception.ErrorCode;
import org.corpsman.rpg.model.ApplicationStatus;
import org.corpsman.rpg.model.Game;
import org.corpsman.rpg.model.GameVisibility;
import org.corpsman.rpg.model.Room;
import org.corpsman.rpg.repository.GameApplicationRepository;
import org.corpsman.rpg.repository.GameWhitelistRepository;
import org.corpsman.rpg.repository.RoomMemberRepository;
import org.springframework.stereotype.Service;

/**
 * Сервис проверки прав в рамках игры: видимость по ролям (аналог форума),
 * ведущий/игрок, доступ к комнатам.
 */
@Service
@RequiredArgsConstructor
public class GameAccessService {

    private final GameWhitelistRepository whitelistRepository;

    private final GameApplicationRepository applicationRepository;

    private final RoomMemberRepository roomMemberRepository;

    /**
     * Видна ли игра зрителю (по роли и белому списку).
     */
    public boolean canView(Game game, UserRole role, Long viewerId) {
        if (viewerId != null && viewerId.equals(game.getMasterId())) {
            return true;
        }
        GameVisibility v = game.getVisibility();
        if (v == GameVisibility.PUBLIC) {
            return true;
        }
        if (v == GameVisibility.CLOSED) {
            return viewerId != null && whitelistRepository.existsByGameIdAndUserId(game.getId(), viewerId);
        }
        if (v == GameVisibility.GUEST) {
            return true;
        }
        if (role == UserRole.GUEST) {
            return false;
        }
        return viewerId != null;
    }

    /**
     * Бросает исключение, если зритель не видит игру.
     */
    public void requireView(Game game, UserRole role, Long viewerId) {
        if (!canView(game, role, viewerId)) {
            throw new BusinessException(ErrorCode.GAME_PERMISSION_DENIED);
        }
    }

    /**
     * Является ли пользователь ведущим игры.
     */
    public boolean isMaster(Game game, Long userId) {
        return userId != null && game.getMasterId().equals(userId);
    }

    /**
     * Является ли пользователь принятым игроком игры.
     */
    public boolean isPlayer(Game game, Long userId) {
        return userId != null && applicationRepository.existsByGameIdAndUserIdAndStatus(
                game.getId(), userId, ApplicationStatus.ACCEPTED);
    }

    /**
     * Может ли пользователь участвовать в комнате (писать посты).
     */
    public boolean canPostInRoom(Game game, Long userId) {
        return isMaster(game, userId) || isPlayer(game, userId);
    }

    /**
     * Видит ли пользователь комнату (чтение).
     */
    public boolean canEnterRoom(Game game, Room room, UserRole role, Long viewerId) {
        if (isMaster(game, viewerId)) {
            return true;
        }
        if (!canView(game, role, viewerId)) {
            return false;
        }
        if (!room.isPrivateRoom()) {
            return true;
        }
        return viewerId != null && roomMemberRepository.existsByRoomIdAndUserId(room.getId(), viewerId);
    }

    /**
     * Бросает исключение, если нет доступа к комнате.
     */
    public void requireRoomAccess(Game game, Room room, UserRole role, Long viewerId) {
        if (!canEnterRoom(game, room, role, viewerId)) {
            throw new BusinessException(ErrorCode.ROOM_ACCESS_DENIED);
        }
    }
}