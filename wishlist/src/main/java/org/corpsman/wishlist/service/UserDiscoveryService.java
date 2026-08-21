package org.corpsman.wishlist.service;

import lombok.RequiredArgsConstructor;
import org.corpsman.auth.model.User;
import org.corpsman.auth.repository.UserRepository;
import org.corpsman.wishlist.dto.UserGiftsSummaryDto;
import org.corpsman.wishlist.repository.GiftRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Сервис поиска пользователей, имеющих подарки в вишлисте.
 */
@Service
@RequiredArgsConstructor
public class UserDiscoveryService {

    private final GiftRepository giftRepository;
    private final UserRepository userRepository;

    /**
     * Возвращает страницу пользователей с подарками, отфильтрованную по нику.
     *
     * @param search   поиск по нику (case-insensitive, частичное совпадение; может быть null)
     * @param pageable пагинация и сортировка
     * @return страница сводок по пользователям
     */
    @Transactional(readOnly = true)
    public Page<UserGiftsSummaryDto> getUsersWithGifts(String search, Pageable pageable) {
        List<Long> userIds = giftRepository.findDistinctOwnerIds();
        if (userIds.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        String normalizedSearch = (search == null || search.isBlank()) ? null : search.trim();
        Page<User> users = userRepository.searchByIdsIn(userIds, normalizedSearch, pageable);
        return users.map(user -> {
            UserGiftsSummaryDto dto = new UserGiftsSummaryDto();
            dto.setUserId(user.getId());
            dto.setUsername(user.getUsername());
            dto.setGiftCount(giftRepository.countByOwnerId(user.getId()));
            return dto;
        });
    }
}