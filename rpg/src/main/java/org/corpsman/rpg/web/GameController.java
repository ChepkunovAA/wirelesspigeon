package org.corpsman.rpg.web;

import lombok.RequiredArgsConstructor;
import org.corpsman.auth.model.User;
import org.corpsman.auth.model.UserRole;
import org.corpsman.auth.repository.UserRepository;
import org.corpsman.auth.security.SecurityUtils;
import org.corpsman.common.exception.BusinessException;
import org.corpsman.common.exception.ErrorCode;
import org.corpsman.rpg.dto.GameDetailDto;
import org.corpsman.rpg.dto.GameForm;
import org.corpsman.rpg.dto.GameSummaryDto;
import org.corpsman.rpg.model.Game;
import org.corpsman.rpg.model.GameStatus;
import org.corpsman.rpg.model.GameSystem;
import org.corpsman.rpg.model.GameVisibility;
import org.corpsman.rpg.service.GameService;
import org.corpsman.rpg.service.GameSystemLibraryService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Страницы RPG-игр: список, создание, редактирование, видимость, белый список.
 */
@Controller
@RequestMapping("/rpg/games")
@RequiredArgsConstructor
public class GameController {

    private final GameService gameService;

    private final GameSystemLibraryService libraryService;

    private final UserRepository userRepository;

    @GetMapping
    public String games(@RequestParam(value = "search", required = false) String search,
                        @RequestParam(value = "page", required = false, defaultValue = "0") int page,
                        @RequestParam(value = "myPage", required = false, defaultValue = "0") int myPage,
                        Model model) {
        Long uid = SecurityUtils.currentUserId();
        boolean anonymous = uid == null;
        boolean guest = !anonymous && userRepository.findById(uid).map(u -> u.getRole() == UserRole.GUEST).orElse(true);
        // Два раздела с собственной пагинацией: «Мои игры» (веду или участвую)
        // показывается на первой странице, общий список — всё остальное видимое.
        Page<GameSummaryDto> myGames = gameService.ownGamesPage(search, uid, myPage);
        Page<GameSummaryDto> otherGames = gameService.otherGamesPage(search, uid, guest, anonymous, page);
        model.addAttribute("myGames", myGames.getContent());
        model.addAttribute("myPage", myGames);
        model.addAttribute("otherGames", otherGames.getContent());
        model.addAttribute("page", otherGames);
        model.addAttribute("search", search == null ? "" : search);
        model.addAttribute("canCreate", !anonymous);
        return "rpg/games";
    }

    /**
     * Идентификатор текущего пользователя или -1 для анонимного: библиотека
     * систем отдаёт и публичные, и собственные приватные.
     */
    private Long effectiveUid() {
        Long uid = SecurityUtils.currentUserId();
        return uid != null ? uid : -1L;
    }

    @GetMapping("/new")
    public String createForm(@RequestParam(value = "system", required = false) String system,
                             @RequestParam(value = "customSystemId", required = false) Long customSystemId,
                             Model model) {
        GameForm form = new GameForm();
        if (isGuest()) {
            form.setVisibility(GameVisibility.GUEST);
        }
        // Предвыбор из ссылки «Создать игру на этой системе».
        if (system != null && !system.isBlank()) {
            try {
                form.setSystem(GameSystem.valueOf(system));
            } catch (IllegalArgumentException ignored) {
                // неизвестная система — остаётся значение по умолчанию
            }
        }
        if (customSystemId != null) {
            form.setCustomSystemId(customSystemId);
        }
        model.addAttribute("gameForm", form);
        model.addAttribute("systems", GameSystem.values());
        model.addAttribute("librarySystems", libraryService.getAvailableSystems(effectiveUid()));
        model.addAttribute("visibilities", GameVisibility.values());
        model.addAttribute("statuses", GameStatus.values());
        model.addAttribute("isGuest", isGuest());
        model.addAttribute("isEdit", false);
        model.addAttribute("allUsers", otherActiveUsers());
        return "rpg/game-form";
    }

    @PostMapping
    public String create(@Validated @ModelAttribute("gameForm") GameForm form, BindingResult binding, Model model) {
        if (binding.hasErrors()) {
            model.addAttribute("systems", GameSystem.values());
            model.addAttribute("librarySystems", libraryService.getAvailableSystems(effectiveUid()));
            model.addAttribute("visibilities", GameVisibility.values());
            model.addAttribute("statuses", GameStatus.values());
            model.addAttribute("isGuest", isGuest());
            model.addAttribute("isEdit", false);
            model.addAttribute("allUsers", otherActiveUsers());
            return "rpg/game-form";
        }
        Long uid = SecurityUtils.currentUserId();
        Game game = gameService.create(freshRole(uid), uid, form);
        return "redirect:/rpg/games/" + game.getId();
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Long uid = SecurityUtils.currentUserId();
        Game game = gameService.requireVisible(id, freshRole(uid), uid);
        if (uid == null && game.getVisibility() != GameVisibility.PUBLIC) {
            throw new BusinessException(ErrorCode.GAME_PERMISSION_DENIED);
        }
        GameDetailDto dto = gameService.detail(game, freshRole(uid), uid);
        model.addAttribute("game", dto);
        model.addAttribute("authenticated", uid != null);
        if (dto.isMasterViewer()) {
            model.addAttribute("whitelistNames", dto.getWhitelistNames());
            List<User> active = userRepository.findByActiveTrueOrderByUsernameAsc();
            Set<Long> wlIds = new HashSet<>(gameService.whitelistUserIds(game.getId()));
            model.addAttribute("whitelistedUsers",
                    active.stream().filter(u -> wlIds.contains(u.getId())).toList());
            model.addAttribute("addableUsers",
                    active.stream().filter(u -> !wlIds.contains(u.getId()) && !u.getId().equals(game.getMasterId())).toList());
        }
        return "rpg/game";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Long uid = SecurityUtils.currentUserId();
        Game game = gameService.requireMaster(id, uid);
        GameForm form = new GameForm();
        form.setTitle(game.getTitle());
        form.setDescription(game.getDescription());
        form.setSystem(game.getSystem());
        form.setStatus(game.getStatus());
        form.setAttributesVisible(game.isAttributesVisible());
        if (isGuest()) {
            form.setVisibility(GameVisibility.GUEST);
        } else if (game.getVisibility() == GameVisibility.GUEST) {
            form.setVisibility(GameVisibility.OPEN);
            form.setGuestAccess(true);
        } else {
            form.setVisibility(game.getVisibility());
        }
        model.addAttribute("gameForm", form);
        model.addAttribute("gameId", game.getId());
        model.addAttribute("users", userRepository.findByActiveTrueOrderByUsernameAsc());
        model.addAttribute("allUsers", otherActiveUsers());
        model.addAttribute("gameWhitelist", gameService.whitelistUserIds(game.getId()));
        model.addAttribute("systems", GameSystem.values());
        model.addAttribute("librarySystems", libraryService.getAvailableSystems(effectiveUid()));
        model.addAttribute("visibilities", GameVisibility.values());
        model.addAttribute("statuses", GameStatus.values());
        model.addAttribute("isGuest", isGuest());
        model.addAttribute("isEdit", true);
        return "rpg/game-form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id,
                         @Validated @ModelAttribute("gameForm") GameForm form,
                         BindingResult binding, Model model) {
        if (binding.hasErrors()) {
            model.addAttribute("gameId", id);
            model.addAttribute("users", userRepository.findByActiveTrueOrderByUsernameAsc());
            model.addAttribute("allUsers", otherActiveUsers());
            model.addAttribute("gameWhitelist", gameService.whitelistUserIds(id));
            model.addAttribute("systems", GameSystem.values());
            model.addAttribute("visibilities", GameVisibility.values());
            model.addAttribute("statuses", GameStatus.values());
            model.addAttribute("isGuest", isGuest());
            model.addAttribute("isEdit", true);
            return "rpg/game-form";
        }
        gameService.update(SecurityUtils.currentUserId(), id, form);
        return "redirect:/rpg/games/" + id;
    }

    @PostMapping("/{id}/whitelist/add")
    public String addWhitelist(@PathVariable Long id, @RequestParam Long userId) {
        gameService.addWhitelist(SecurityUtils.currentUserId(), id, userId);
        return "redirect:/rpg/games/" + id;
    }

    @PostMapping("/{id}/whitelist/remove")
    public String removeWhitelist(@PathVariable Long id, @RequestParam Long userId) {
        gameService.removeWhitelist(SecurityUtils.currentUserId(), id, userId);
        return "redirect:/rpg/games/" + id;
    }

    private boolean isGuest() {
        var user = SecurityUtils.currentUser();
        return user != null && user.getRole() == UserRole.GUEST;
    }

    private List<User> otherActiveUsers() {
        Long me = SecurityUtils.currentUserId();
        return userRepository.findByActiveTrueOrderByUsernameAsc().stream()
                .filter(u -> !u.getId().equals(me))
                .toList();
    }

    private UserRole freshRole(Long uid) {
        if (uid == null) {
            return null;
        }
        return userRepository.findById(uid).map(u -> u.getRole()).orElse(null);
    }
}