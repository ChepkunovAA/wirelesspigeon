package org.corpsman.wishlist.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.corpsman.auth.model.User;
import org.corpsman.auth.security.SecurityUtils;
import org.corpsman.auth.service.UserService;
import org.corpsman.common.exception.BusinessException;
import org.corpsman.common.exception.ErrorCode;
import org.corpsman.wishlist.dto.GiftCreateDto;
import org.corpsman.wishlist.service.UserDiscoveryService;
import org.corpsman.wishlist.service.WishlistService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

/**
 * Контроллер вишлистов: свои и чужие списки, резервирование подарков.
 */
@Controller
@RequestMapping("/gifts")
@RequiredArgsConstructor
@Slf4j
public class WishlistController {

    private static final int USERS_PAGE_SIZE = 20;

    private final WishlistService wishlistService;
    private final UserDiscoveryService userDiscoveryService;
    private final UserService userService;

    /**
     * Отображает собственный вишлист.
     *
     * @param model модель представления
     * @return имя представления
     */
    @GetMapping
    public String myGifts(Model model) {
        Long userId = SecurityUtils.currentUserId();
        model.addAttribute("gifts", wishlistService.getUserGifts(userId, userId));
        return "gifts/my-list";
    }

    /**
     * Отображает форму добавления подарка.
     *
     * @param model модель представления
     * @return имя представления
     */
    @GetMapping("/add")
    public String showAddForm(Model model) {
        model.addAttribute("gift", new GiftCreateDto());
        return "gifts/add";
    }

    /**
     * Добавляет подарок.
     *
     * @param dto    данные подарка
     * @param result результат валидации
     * @return редирект или форма с ошибками
     */
    @PostMapping("/add")
    public String addGift(@Valid @ModelAttribute("gift") GiftCreateDto dto, BindingResult result) {
        if (result.hasErrors()) {
            return "gifts/add";
        }
        wishlistService.addGift(dto, SecurityUtils.currentUserId());
        return "redirect:/gifts";
    }

    /**
     * Отображает форму редактирования подарка.
     *
     * @param id    идентификатор подарка
     * @param model модель представления
     * @return имя представления
     */
    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model) {
        model.addAttribute("gift", wishlistService.getGiftForEdit(id, SecurityUtils.currentUserId()));
        return "gifts/edit";
    }

    /**
     * Обновляет подарок.
     *
     * @param id           идентификатор подарка
     * @param dto          новые данные
     * @param result       результат валидации
     * @param confirmExcess подтверждение уменьшения лимита при активных резервах
     * @param model        модель представления
     * @return редирект или форма с ошибками/предупреждением
     */
    @PostMapping("/edit/{id}")
    public String updateGift(@PathVariable Long id,
                             @Valid @ModelAttribute("gift") GiftCreateDto dto,
                             BindingResult result,
                             @RequestParam(defaultValue = "false") boolean confirmExcess,
                             Model model) {
        if (result.hasErrors()) {
            return "gifts/edit";
        }
        try {
            wishlistService.updateGift(id, dto, SecurityUtils.currentUserId(), confirmExcess);
            return "redirect:/gifts";
        } catch (BusinessException ex) {
            if (ex.getErrorCode() != ErrorCode.LIMIT_TOO_LOW) {
                throw ex;
            }
            model.addAttribute("warning", ex.getMessage());
            return "gifts/edit";
        }
    }

    /**
     * Удаляет подарок.
     *
     * @param id идентификатор подарка
     * @return редирект на свой список
     */
    @PostMapping("/delete/{id}")
    public String deleteGift(@PathVariable Long id) {
        wishlistService.deleteGift(id, SecurityUtils.currentUserId());
        return "redirect:/gifts";
    }

    /**
     * Отображает список пользователей, имеющих подарки.
     *
     * @param search поиск по нику
     * @param page   номер страницы
     * @param model  модель представления
     * @return имя представления
     */
    @GetMapping("/users")
    public String userList(@RequestParam(defaultValue = "") String search,
                           @RequestParam(defaultValue = "0") int page,
                           Model model) {
        Pageable pageable = PageRequest.of(page, USERS_PAGE_SIZE, Sort.by("username"));
        model.addAttribute("users", userDiscoveryService.getUsersWithGifts(search, pageable));
        model.addAttribute("search", search);
        return "gifts/users";
    }

    /**
     * Отображает вишлист другого пользователя.
     *
     * @param userId идентификатор владельца списка
     * @param model  модель представления
     * @return имя представления или редирект на свой список
     */
    @GetMapping("/user/{userId}")
    public String userGifts(@PathVariable Long userId, Model model) {
        Long currentUserId = SecurityUtils.currentUserId();
        if (currentUserId != null && currentUserId.equals(userId)) {
            return "redirect:/gifts";
        }
        User user = userService.findEntity(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        model.addAttribute("gifts", wishlistService.getUserGifts(userId, currentUserId));
        model.addAttribute("ownerName", user.getUsername());
        return "gifts/user-list";
    }

    /**
     * Резервирует подарок (AJAX).
     *
     * @param giftId идентификатор подарка
     * @return результат резервирования
     */
    @PostMapping("/reserve/{giftId}")
    @ResponseBody
    public ResponseEntity<?> reserveGift(@PathVariable Long giftId) {
        try {
            return ResponseEntity.ok(wishlistService.reserveGift(giftId, SecurityUtils.currentUserId()));
        } catch (BusinessException ex) {
            return ResponseEntity.status(ex.getErrorCode().getHttpStatus())
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    /**
     * Отменяет резерв (AJAX).
     *
     * @param giftId идентификатор подарка
     * @return результат отмены
     */
    @PostMapping("/unreserve/{giftId}")
    @ResponseBody
    public ResponseEntity<?> unreserveGift(@PathVariable Long giftId) {
        try {
            return ResponseEntity.ok(wishlistService.unreserveGift(giftId, SecurityUtils.currentUserId()));
        } catch (BusinessException ex) {
            return ResponseEntity.status(ex.getErrorCode().getHttpStatus())
                    .body(Map.of("error", ex.getMessage()));
        }
    }
}