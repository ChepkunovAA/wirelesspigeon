package org.corpsman.webapp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.corpsman.auth.security.CorpsmanUserDetails;
import org.corpsman.auth.model.User;
import org.corpsman.auth.model.UserRole;
import org.corpsman.auth.repository.UserRepository;
import org.corpsman.auth.security.SecurityUtils;
import org.corpsman.cinema.service.CinemaService;
import org.corpsman.cinema.service.RatingService;
import org.corpsman.common.exception.BusinessException;
import org.corpsman.forum.service.ForumService;
import org.corpsman.forum.service.RatingStatsService;
import org.corpsman.forum.service.SubscriptionService;
import org.corpsman.wishlist.service.WishlistService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Контроллер главных страниц приложения.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class MainController {

    private final RatingService ratingService;

    private final WishlistService wishlistService;

    private final CinemaService cinemaService;

    private final ForumService forumService;

    private final RatingStatsService ratingStatsService;

    private final SubscriptionService subscriptionService;

    private final UserRepository userRepository;

    /**
     * Отображает главную страницу приложения.
     *
     * @param model модель представления
     * @return имя представления
     */
    @GetMapping("/")
    public String home(Model model) {
        CorpsmanUserDetails user = SecurityUtils.currentUser();
        if (user != null) {
            model.addAttribute("user", user);
        }
        boolean residentPlus = user != null && (user.getRole() == org.corpsman.auth.model.UserRole.RESIDENT
                || user.getRole() == org.corpsman.auth.model.UserRole.MASTER);
        model.addAttribute("filmsHidden", !residentPlus);
        model.addAttribute("dashboard", cinemaService.getDashboard());
        Long viewerId = user == null ? null : user.getId();
        model.addAttribute("latestPost", forumService.getLatestVisiblePost(viewerId));
        model.addAttribute("bestPost", forumService.getBestVisiblePost(viewerId));
        return "home";
    }

    /**
     * Отображает личный кабинет пользователя.
     *
     * @param model модель представления
     * @return имя представления
     */
    @GetMapping("/profile")
    public String profile(Model model) {
        CorpsmanUserDetails user = SecurityUtils.currentUser();
        if (user != null) {
            model.addAttribute("user", user);
            boolean ownGuest = user.getRole() == org.corpsman.auth.model.UserRole.GUEST;
            model.addAttribute("ownCinemaHidden", ownGuest);
            if (!ownGuest) {
                model.addAttribute("userFilmsStats", ratingService.getUserFilmsStats(user.getId()));
            }
            model.addAttribute("corruption", ratingService.getUserCorruption(user.getId()));
            model.addAttribute("myGifts", wishlistService.getUserGifts(user.getId(), user.getId()));
            model.addAttribute("forumRating", ratingStatsService.statsFor(user.getId()));
        }
        return "profile";
    }

    /**
     * Отображает публичный профиль другого пользователя.
     *
     * @param id    идентификатор пользователя
     * @param model модель представления
     * @return имя представления или редирект на свой профиль
     */
    @GetMapping("/profile/{id}")
    public String userProfile(@PathVariable Long id, Model model) {
        Long currentId = SecurityUtils.currentUserId();
        model.addAttribute("currentUserId", currentId);
        if (currentId != null && currentId.equals(id)) {
            return "redirect:/profile";
        }
        try {
            model.addAttribute("profile", ratingService.getPublicProfile(id, currentId));
            User profileOwner = userRepository.findById(id).orElse(null);
            // Информация о пригласившем пользователе (для администраторов)
            if (profileOwner != null && profileOwner.getInvitedBy() != null) {
                userRepository.findById(profileOwner.getInvitedBy())
                        .ifPresent(inviter -> model.addAttribute("inviter", inviter));
            }
            boolean viewerGuest = SecurityUtils.currentUser() != null
                    && SecurityUtils.currentUser().getRole() == org.corpsman.auth.model.UserRole.GUEST;
            boolean profileGuest = profileOwner != null
                    && profileOwner.getRole() == UserRole.GUEST;
            model.addAttribute("cinemaHidden", viewerGuest || profileGuest);
            model.addAttribute("subscribedToUser",
                    subscriptionService.isSubscribedToTopic(currentId, id)
                            || subscriptionService.isSubscribedToUser(currentId, id));
            model.addAttribute("gifts", wishlistService.getUserGifts(id, currentId));
            model.addAttribute("forumRating", ratingStatsService.statsFor(id));
            if (currentId != null) {
                model.addAttribute("targetToMe", ratingStatsService.pairwise(id, currentId));
                model.addAttribute("meToTarget", ratingStatsService.pairwise(currentId, id));
            }
        } catch (BusinessException ex) {
            model.addAttribute("error", ex.getMessage());
            return "profile-user";
        }
        return "profile-user";
    }
}
