package org.corpsman.rpg.web;

import lombok.RequiredArgsConstructor;
import org.corpsman.auth.model.UserRole;
import org.corpsman.auth.repository.UserRepository;
import org.corpsman.auth.security.SecurityUtils;
import org.corpsman.rpg.dto.CharacterModifierDto;
import org.corpsman.rpg.dto.RoomDetailDto;
import org.corpsman.rpg.dto.RoomForm;
import org.corpsman.rpg.dto.RoomPersonaDto;
import org.corpsman.rpg.dto.SavedRollDto;
import org.corpsman.rpg.dto.SavedRollForm;
import org.corpsman.rpg.model.CharacterSheet;
import org.corpsman.rpg.model.DiceSize;
import org.corpsman.rpg.model.Game;
import org.corpsman.rpg.model.GameVisibility;
import org.corpsman.rpg.model.Room;
import org.corpsman.rpg.model.RoomPost;
import org.corpsman.rpg.model.RoomPostAttachment;
import org.corpsman.rpg.service.CharacterSheetService;
import org.corpsman.rpg.service.DiceService;
import org.corpsman.rpg.service.GameService;
import org.corpsman.rpg.service.RoomService;
import org.corpsman.rpg.service.character.DndCharacterSystem;
import org.corpsman.common.service.FileStorageService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Комнаты игры: создание, просмотр, посты с бросками, белый список приватной комнаты.
 */
@Controller
@RequestMapping("/rpg/games/{gameId}/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final GameService gameService;

    private final RoomService roomService;

    private final CharacterSheetService characterSheetService;

    private final DiceService diceService;

    private final FileStorageService fileStorageService;

    private final UserRepository userRepository;

    @GetMapping("/new")
    public String createForm(@PathVariable Long gameId, Model model) {
        Long uid = SecurityUtils.currentUserId();
        Game game = gameService.requireMaster(gameId, uid);
        model.addAttribute("game", game);
        model.addAttribute("roomForm", new RoomForm());
        model.addAttribute("playerSheets", characterSheetService.playersFor(game, freshRole(uid), uid));
        return "rpg/room-form";
    }

    @PostMapping
    public String create(@PathVariable Long gameId, @ModelAttribute RoomForm form,
                         @RequestParam(value = "memberIds", required = false) List<Long> memberIds) {
        Long uid = SecurityUtils.currentUserId();
        Game game = gameService.requireMaster(gameId, uid);
        Room room = roomService.createRoom(game, uid, form);
        if (form.isPrivateRoom() && memberIds != null) {
            roomService.setMembers(game, room, uid, memberIds);
        }
        return "redirect:/rpg/games/" + gameId + "/rooms/" + room.getId();
    }

    @GetMapping("/{roomId}")
    public String detail(@PathVariable Long gameId, @PathVariable Long roomId, Model model) {
        Long uid = SecurityUtils.currentUserId();
        Game game = gameService.requireVisible(gameId, freshRole(uid), uid);
        Room room = roomService.requireRoom(game, roomId);
        RoomDetailDto detail = roomService.detail(game, room, freshRole(uid), uid);
        model.addAttribute("game", game);
        model.addAttribute("room", detail);
        model.addAttribute("diceSizes", DiceSize.values());
        model.addAttribute("playerSheets", characterSheetService.playersFor(game, freshRole(uid), uid));
        model.addAttribute("personas", buildPersonas(game, freshRole(uid), uid));
        model.addAttribute("favorites", diceService.savedRollsFor(uid, gameId));
        model.addAttribute("npcFavorites", buildNpcFavorites(game, freshRole(uid), uid));
        model.addAttribute("abilityNames", abilityNames());
        model.addAttribute("skillAbility", DndCharacterSystem.skillAbilityMap());
        model.addAttribute("currentUserId", uid);
        return "rpg/room";
    }

    /**
     * «Избранные» броски мастера, привязанные к конкретным NPC (по id листа).
     * Для не-мастера всегда пусто.
     */
    private Map<Long, List<SavedRollDto>> buildNpcFavorites(Game game, UserRole role, Long viewerId) {
        if (!game.getMasterId().equals(viewerId)) {
            return Map.of();
        }
        Map<Long, List<SavedRollDto>> favorites = new LinkedHashMap<>();
        for (var npc : characterSheetService.npcsFor(game, role, viewerId)) {
            favorites.put(npc.getId(), diceService.savedRollsFor(viewerId, game.getId(), npc.getId()));
        }
        return favorites;
    }

    /**
     * Персоны постинга: «сам» (для всех) и NPC (только у мастера). Каждая персона
     * несёт собственный набор проверок-модификаторов.
     */
    private List<RoomPersonaDto> buildPersonas(Game game, UserRole role, Long viewerId) {
        List<RoomPersonaDto> personas = new ArrayList<>();
        CharacterSheet ownSheet = characterSheetService.resolveSheetByOwnerAndGame(viewerId, game.getId())
                .filter(s -> !s.isNpc())
                .orElse(null);
        String ownName = ownSheet != null ? ownSheet.getName()
                : viewerId == null ? "Вы"
                : userRepository.findById(viewerId).map(u -> u.getUsername()).orElse("Вы");
        RoomPersonaDto self = new RoomPersonaDto();
        self.setId("self");
        self.setName("Вы (" + ownName + ")");
        self.setNpc(false);
        if (ownSheet != null) {
            self.setModifiers(characterSheetService.render(game, ownSheet, role, viewerId).getModifiers());
        } else {
            self.setModifiers(List.of());
        }
        personas.add(self);

        if (game.getMasterId().equals(viewerId)) {
            for (var npc : characterSheetService.npcsFor(game, role, viewerId)) {
                RoomPersonaDto dto = new RoomPersonaDto();
                dto.setId("npc-" + npc.getId());
                dto.setName(npc.getName());
                dto.setNpc(true);
                dto.setModifiers(npc.getModifiers());
                personas.add(dto);
            }
        }
        return personas;
    }

    private Map<String, String> abilityNames() {
        Map<String, String> names = new LinkedHashMap<>();
        for (String key : DndCharacterSystem.abilityKeys()) {
            names.put(key, DndCharacterSystem.abilityName(key));
        }
        return names;
    }

    @PostMapping("/{roomId}/posts")
    public String createPost(@PathVariable Long gameId, @PathVariable Long roomId,
                             @RequestParam String content,
                             @RequestParam(value = "sheetId", required = false) Long sheetId,
                             @RequestParam(value = "files", required = false) List<MultipartFile> files,
                             @RequestParam(value = "die", required = false) List<String> die,
                             @RequestParam(value = "count", required = false) List<Integer> count,
                             @RequestParam(value = "modifier", required = false) List<Integer> modifier,
                             @RequestParam(value = "rollComment", required = false) List<String> rollComment) {
        Long uid = SecurityUtils.currentUserId();
        Game game = gameService.find(gameId);
        Room room = roomService.requireRoom(game, roomId);
        roomService.createPost(game, room, uid, sheetId, content, buildRolls(die, count, modifier, rollComment), files);
        return "redirect:/rpg/games/" + gameId + "/rooms/" + roomId;
    }

    /**
     * Сохранение комбинации бросков как «Избранное». Сохранить можно только бросок
     * своего поста; броски, опубликованные мастером от имени NPC, попадают во
     * «вложенное Избранное» этого NPC.
     */
    @PostMapping("/{roomId}/saved-rolls")
    @ResponseBody
    public Map<String, Object> saveFavorite(@PathVariable Long gameId, @PathVariable Long roomId,
                                            @RequestParam(required = false) Long postId,
                                            @RequestParam String die,
                                            @RequestParam(defaultValue = "1") Integer count,
                                            @RequestParam(defaultValue = "0") Integer modifier,
                                            @RequestParam(value = "rollComment", required = false) String rollComment) {
        Long uid = SecurityUtils.currentUserId();
        Game game = gameService.find(gameId);
        Room room = roomService.requireRoom(game, roomId);
        if (!roomService.canPostInRoom(game, uid)) {
            return Map.of("ok", false, "error", "Нет доступа");
        }
        Long sheetId = null;
        if (postId != null) {
            RoomPost post = roomService.requirePost(room, postId);
            if (!post.getAuthorId().equals(uid)) {
                return Map.of("ok", false, "error", "Сохранять можно только свои броски");
            }
            sheetId = post.getSheetId();
        }
        SavedRollForm form = new SavedRollForm();
        form.setDie(DiceSize.byLabel(die));
        form.setCount(count);
        form.setModifier(modifier);
        form.setComment(rollComment);
        diceService.saveCombination(uid, gameId, sheetId, form);
        return Map.of("ok", true);
    }

    /**
     * Снятие всей комбинации из «Избранного»: исключает этим все броски такого
     * вида (d/count/modifier/comment) у пользователя в рамках данной игры.
     */
    @PostMapping("/{roomId}/saved-rolls/remove-kind")
    @ResponseBody
    public Map<String, Object> removeKindFavorite(@PathVariable Long gameId, @PathVariable Long roomId,
                                                  @RequestParam String die,
                                                  @RequestParam(defaultValue = "1") Integer count,
                                                  @RequestParam(defaultValue = "0") Integer modifier,
                                                  @RequestParam(value = "rollComment", required = false) String rollComment) {
        Long uid = SecurityUtils.currentUserId();
        Game game = gameService.requireVisible(gameId, freshRole(uid), uid);
        DiceSize dieSize = DiceSize.byLabel(die);
        if (dieSize == null) {
            return Map.of("ok", false, "error", "Неверный кубик");
        }
        diceService.deleteCombinationsOfKind(uid, game.getId(), dieSize.getFaces(),
                count == null ? 1 : count, modifier == null ? 0 : modifier, rollComment);
        return Map.of("ok", true);
    }

    /**
     * Удаление конкретного сохранённого броска из «Избранного» (только своего).
     */
    @PostMapping("/{roomId}/saved-rolls/{savedRollId}/delete")
    @ResponseBody
    public Map<String, Object> deleteFavorite(@PathVariable Long gameId, @PathVariable Long roomId,
                                              @PathVariable Long savedRollId) {
        Long uid = SecurityUtils.currentUserId();
        diceService.deleteCombination(uid, savedRollId);
        return Map.of("ok", true);
    }

    /**
     * Картинка или файл из поста комнаты (доступ — как у комнаты).
     */
    @GetMapping("/{roomId}/attachments/{attachmentId}")
    public ResponseEntity<Resource> attachment(@PathVariable Long gameId, @PathVariable Long roomId,
                                               @PathVariable Long attachmentId) {
        Long uid = SecurityUtils.currentUserId();
        Game game = gameService.requireVisible(gameId, freshRole(uid), uid);
        Room room = roomService.requireRoom(game, roomId);
        roomService.requireRoomAccess(game, room, freshRole(uid), uid);
        RoomPostAttachment attachment = roomService.requireAttachment(game, room, attachmentId);
        Resource resource = new FileSystemResource(fileStorageService.load(attachment.getStoredName()));
        boolean inline = attachment.getContentType().startsWith("image/")
                || attachment.getContentType().startsWith("video/");
        ContentDisposition disposition = inline
                ? ContentDisposition.inline().filename(attachment.getFileName(), StandardCharsets.UTF_8).build()
                : ContentDisposition.attachment().filename(attachment.getFileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .contentType(MediaType.parseMediaType(attachment.getContentType()))
                .contentLength(attachment.getSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(resource);
    }

    @PostMapping("/{roomId}/members")
    public String setMembers(@PathVariable Long gameId, @PathVariable Long roomId,
                             @RequestParam(value = "memberIds", required = false) List<Long> memberIds) {
        Game game = gameService.requireMaster(gameId, SecurityUtils.currentUserId());
        Room room = roomService.requireRoom(game, roomId);
        roomService.setMembers(game, room, SecurityUtils.currentUserId(),
                memberIds == null ? List.of() : memberIds);
        return "redirect:/rpg/games/" + gameId + "/rooms/" + roomId;
    }

    private List<org.corpsman.rpg.dto.DieRollForm> buildRolls(List<String> die, List<Integer> count,
                                                              List<Integer> modifier, List<String> rollComment) {
        List<org.corpsman.rpg.dto.DieRollForm> result = new ArrayList<>();
        if (die == null) {
            return result;
        }
        for (int i = 0; i < die.size(); i++) {
            String label = die.get(i);
            if (label == null || label.isBlank()) {
                continue;
            }
            org.corpsman.rpg.dto.DieRollForm f = new org.corpsman.rpg.dto.DieRollForm();
            DiceSize parsed = DiceSize.byLabel(label);
            if (parsed != null) {
                f.setDie(parsed);
                f.setCount(i < count.size() ? count.get(i) : 1);
                f.setModifier(i < modifier.size() ? modifier.get(i) : 0);
                f.setComment(i < rollComment.size() ? rollComment.get(i) : null);
                result.add(f);
            }
        }
        return result;
    }

    private UserRole freshRole(Long uid) {
        if (uid == null) {
            return null;
        }
        return userRepository.findById(uid).map(u -> u.getRole()).orElse(null);
    }
}