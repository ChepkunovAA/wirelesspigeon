package org.corpsman.rpg.service;

import lombok.RequiredArgsConstructor;
import org.corpsman.auth.model.UserRole;
import org.corpsman.auth.repository.UserRepository;
import org.corpsman.common.exception.BusinessException;
import org.corpsman.common.exception.ErrorCode;
import org.corpsman.common.service.FileStorageService;
import org.corpsman.rpg.dto.DiceRollDto;
import org.corpsman.rpg.dto.RoomAttachmentDto;
import org.corpsman.rpg.dto.RoomDto;
import org.corpsman.rpg.dto.RoomForm;
import org.corpsman.rpg.dto.RoomPostDto;
import org.corpsman.rpg.model.CharacterSheet;
import org.corpsman.rpg.model.DiceRoll;
import org.corpsman.rpg.model.Game;
import org.corpsman.rpg.model.Room;
import org.corpsman.rpg.model.RoomMember;
import org.corpsman.rpg.model.RoomPost;
import org.corpsman.rpg.model.RoomPostAttachment;
import org.corpsman.rpg.repository.DiceRollRepository;
import org.corpsman.rpg.repository.RoomMemberRepository;
import org.corpsman.rpg.repository.RoomPostAttachmentRepository;
import org.corpsman.rpg.repository.RoomPostRepository;
import org.corpsman.rpg.repository.RoomRepository;
import org.corpsman.rpg.dto.RoomDetailDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * Сервис комнат внутри игры: создание, доступ, посты с бросками костей.
 */
@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;

    private final RoomMemberRepository memberRepository;

    private final RoomPostRepository postRepository;

    private final DiceRollRepository diceRollRepository;

    private final RoomPostAttachmentRepository attachmentRepository;

    private final GameAccessService accessService;

    private final UserRepository userRepository;

    private final CharacterSheetService characterSheetService;

    private final FileStorageService fileStorageService;

    private final DiceService diceService;

    private final GameNotificationPublisher notificationPublisher;

    @Transactional
    public Room createRoom(Game game, Long masterId, RoomForm form) {
        if (!accessService.isMaster(game, masterId)) {
            throw new BusinessException(ErrorCode.GAME_PERMISSION_DENIED);
        }
        Room room = new Room();
        room.setGameId(game.getId());
        room.setTitle(form.getTitle().trim());
        room.setPrivateRoom(form.isPrivateRoom());
        return roomRepository.save(room);
    }

    @Transactional(readOnly = true)
    public List<RoomDto> roomsFor(Game game, UserRole role, Long viewerId) {
        return roomRepository.findByGameIdOrderByCreatedAtDesc(game.getId()).stream()
                .filter(r -> accessService.canView(game, role, viewerId))
                .map(r -> {
                    RoomDto dto = toDto(r);
                    dto.setAccessible(accessService.canEnterRoom(game, r, role, viewerId));
                    return dto;
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public Room requireRoom(Game game, Long roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));
        if (!room.getGameId().equals(game.getId())) {
            throw new BusinessException(ErrorCode.ROOM_NOT_FOUND);
        }
        return room;
    }

    @Transactional(readOnly = true)
    public RoomDetailDto detail(Game game, Room room, UserRole role, Long viewerId) {
        accessService.requireRoomAccess(game, room, role, viewerId);
        RoomDetailDto dto = new RoomDetailDto();
        dto.setId(room.getId());
        dto.setGameId(room.getGameId());
        dto.setTitle(room.getTitle());
        dto.setPrivateRoom(room.isPrivateRoom());
        dto.setMasterViewer(accessService.isMaster(game, viewerId));
        dto.setCanPost(accessService.canPostInRoom(game, viewerId));
        dto.setMasterId(game.getMasterId());
        List<RoomPost> posts = postRepository.findByRoomIdOrderByCreatedAtAsc(room.getId());
        List<RoomPostDto> postDtos = new ArrayList<>();
        for (RoomPost post : posts) {
            postDtos.add(toPostDto(game, post));
        }
        dto.setPosts(postDtos);

        List<Long> memberIds = memberRepository.findByRoomId(room.getId()).stream()
                .map(RoomMember::getUserId)
                .toList();
        dto.setMemberIds(memberIds);
        dto.setUsernames(memberIds.stream()
                .map(id -> userRepository.findById(id).map(u -> u.getUsername()).orElse("—"))
                .toList());
        return dto;
    }

    @Transactional
    public RoomPost createPost(Game game, Room room, Long authorId, Long sheetId, String content,
                               List<org.corpsman.rpg.dto.DieRollForm> rolls,
                               List<MultipartFile> files) {
        if (!accessService.canPostInRoom(game, authorId)) {
            throw new BusinessException(ErrorCode.ROOM_ACCESS_DENIED);
        }
        if (sheetId != null) {
            CharacterSheet sheet = characterSheetService.resolveSheet(sheetId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.SHEET_NOT_FOUND));
            if (!game.getId().equals(sheet.getGameId()) || !sheet.isNpc()) {
                throw new BusinessException(ErrorCode.SHEET_NOT_FOUND);
            }
            if (!accessService.isMaster(game, authorId)) {
                throw new BusinessException(ErrorCode.ROOM_ACCESS_DENIED);
            }
        }
        RoomPost post = new RoomPost();
        post.setRoomId(room.getId());
        post.setAuthorId(authorId);
        post.setSheetId(sheetId);
        post.setContent(content == null ? "" : content.trim());
        post = postRepository.save(post);

        for (org.corpsman.rpg.dto.DieRollForm form : rolls) {
            DiceRoll roll = diceService.roll(form);
            roll.setPostId(post.getId());
            diceRollRepository.save(roll);
        }
        attachFiles(files, post.getId());
        notificationPublisher.roomPostCreated(game, room, authorId, accessService.isMaster(game, authorId));
        return post;
    }

    /**
     * Прикрепляет загруженные файлы к посту комнаты.
     */
    private void attachFiles(List<MultipartFile> files, Long postId) {
        if (files == null || files.isEmpty()) {
            return;
        }
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            RoomPostAttachment attachment = new RoomPostAttachment();
            attachment.setPostId(postId);
            attachment.setFileName(StringUtils.hasText(file.getOriginalFilename())
                    ? file.getOriginalFilename() : "file");
            attachment.setContentType(StringUtils.hasText(file.getContentType())
                    ? file.getContentType() : "application/octet-stream");
            attachment.setSize(file.getSize());
            attachment.setStoredName(fileStorageService.store(file));
            attachmentRepository.save(attachment);
        }
    }

    @Transactional(readOnly = true)
    public RoomPost requirePost(Room room, Long postId) {
        RoomPost post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_POST_NOT_FOUND));
        if (!post.getRoomId().equals(room.getId())) {
            throw new BusinessException(ErrorCode.ROOM_POST_NOT_FOUND);
        }
        return post;
    }

    /**
     * Вложение заданной комнаты (пост вложения должен принадлежать комнате).
     */
    @Transactional(readOnly = true)
    public RoomPostAttachment requireAttachment(Game game, Room room, Long attachmentId) {
        RoomPostAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));
        RoomPost post = postRepository.findById(attachment.getPostId())
                .orElseThrow(() -> new BusinessException(ErrorCode.FILE_NOT_FOUND));
        if (!post.getRoomId().equals(room.getId())) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        return attachment;
    }

    @Transactional
    public void setMembers(Game game, Room room, Long masterId, List<Long> userIds) {
        if (!accessService.isMaster(game, masterId)) {
            throw new BusinessException(ErrorCode.GAME_PERMISSION_DENIED);
        }
        List<RoomMember> existing = memberRepository.findByRoomId(room.getId());
        List<Long> wanted = userIds == null ? List.of() : userIds;
        for (RoomMember m : existing) {
            if (!wanted.contains(m.getUserId())) {
                memberRepository.delete(m);
            }
        }
        for (Long uid : wanted) {
            if (!memberRepository.existsByRoomIdAndUserId(room.getId(), uid)) {
                RoomMember m = new RoomMember();
                m.setRoomId(room.getId());
                m.setUserId(uid);
                memberRepository.save(m);
            }
        }
    }

    private RoomPostDto toPostDto(Game game, RoomPost post) {
        RoomPostDto dto = new RoomPostDto();
        dto.setId(post.getId());
        dto.setAuthorId(post.getAuthorId());
        boolean ownMasterPost = post.getSheetId() == null && game.getMasterId().equals(post.getAuthorId());
        if (post.getSheetId() != null) {
            java.util.Optional<CharacterSheet> sheet = characterSheetService.resolveSheet(post.getSheetId());
            dto.setAuthorName(sheet.map(CharacterSheet::getName).orElse("NPC"));
            dto.setAvatarPath(sheet.map(CharacterSheet::getAvatarPath).orElse(null));
        } else if (ownMasterPost) {
            dto.setAuthorName("Мастер");
            dto.setAvatarPath(userRepository.findById(post.getAuthorId()).map(u -> u.getAvatarPath()).orElse(null));
        } else {
            java.util.Optional<CharacterSheet> sheet = characterSheetService
                    .resolveSheetByOwnerAndGame(post.getAuthorId(), game.getId())
                    .filter(s -> !s.isNpc());
            dto.setAuthorName(sheet.map(CharacterSheet::getName)
                    .orElse(userRepository.findById(post.getAuthorId()).map(u -> u.getUsername()).orElse("—")));
            dto.setAvatarPath(sheet.map(CharacterSheet::getAvatarPath)
                    .orElse(userRepository.findById(post.getAuthorId()).map(u -> u.getAvatarPath()).orElse(null)));
        }
        dto.setMasterPost(ownMasterPost);
        dto.setContent(post.getContent());
        dto.setCreatedAt(post.getCreatedAt());
        List<DiceRollDto> rollDtos = diceRollRepository.findByPostIdOrderByIdAsc(post.getId()).stream()
                .map(diceService::toDto)
                .toList();
        dto.setRolls(rollDtos);
        dto.setAttachments(attachmentRepository.findByPostIdOrderByIdAsc(post.getId()).stream()
                .map(this::toAttachmentDto)
                .toList());
        return dto;
    }

    private RoomAttachmentDto toAttachmentDto(RoomPostAttachment a) {
        RoomAttachmentDto dto = new RoomAttachmentDto();
        dto.setId(a.getId());
        dto.setFileName(a.getFileName());
        dto.setContentType(a.getContentType());
        dto.setSize(a.getSize());
        return dto;
    }

    private RoomDto toDto(Room room) {
        RoomDto dto = new RoomDto();
        dto.setId(room.getId());
        dto.setGameId(room.getGameId());
        dto.setTitle(room.getTitle());
        dto.setPrivateRoom(room.isPrivateRoom());
        return dto;
    }

    /**
     * Может ли пользователь писать в комнате.
     */
    public boolean canPostInRoom(Game game, Long userId) {
        return accessService.canPostInRoom(game, userId);
    }

    /**
     * Проверяет право входа в комнату.
     */
    public void requireRoomAccess(Game game, Room room, UserRole role, Long viewerId) {
        accessService.requireRoomAccess(game, room, role, viewerId);
    }
}