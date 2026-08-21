package org.corpsman.wishlist.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.corpsman.auth.mapper.UserMapper;
import org.corpsman.auth.model.User;
import org.corpsman.auth.repository.UserRepository;
import org.corpsman.common.dto.UserDto;
import org.corpsman.common.event.GiftReservedEvent;
import org.corpsman.common.exception.BusinessException;
import org.corpsman.common.exception.ErrorCode;
import org.corpsman.wishlist.dto.GiftCreateDto;
import org.corpsman.wishlist.dto.GiftDto;
import org.corpsman.wishlist.dto.ReservationResponseDto;
import org.corpsman.wishlist.model.Gift;
import org.corpsman.wishlist.model.GiftReservation;
import org.corpsman.wishlist.repository.GiftRepository;
import org.corpsman.wishlist.repository.GiftReservationRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Сервис вишлиста: управление подарками и резервирование.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WishlistService {

    private final GiftRepository giftRepository;
    private final GiftReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Возвращает список подарков пользователя.
     *
     * @param userId        владелец списка
     * @param currentUserId текущий пользователь
     * @return список подарков
     */
    @Transactional(readOnly = true)
    public List<GiftDto> getUserGifts(Long userId, Long currentUserId) {
        return giftRepository.findByOwnerIdOrderByCreatedAtDesc(userId).stream()
                .map(gift -> toDto(gift, currentUserId))
                .toList();
    }

    /**
     * Добавляет новый подарок в вишлист.
     *
     * @param dto    данные подарка
     * @param userId владелец
     * @return созданный подарок
     */
    @Transactional
    public GiftDto addGift(GiftCreateDto dto, Long userId) {
        Gift gift = new Gift();
        gift.setTitle(dto.getTitle().trim());
        gift.setDescription(dto.getDescription());
        gift.setLimit(dto.getLimit());
        gift.setOwnerId(userId);
        gift.setReservedCount(0);
        Gift saved = giftRepository.save(gift);
        log.info("Добавлен подарок {} (ID: {}) для пользователя {}", saved.getTitle(), saved.getId(), userId);
        return toDto(saved, userId);
    }

    /**
     * Возвращает данные подарка для формы редактирования (только владелец).
     *
     * @param giftId идентификатор подарка
     * @param userId текущий пользователь
     * @return данные для формы
     */
    @Transactional(readOnly = true)
    public GiftCreateDto getGiftForEdit(Long giftId, Long userId) {
        Gift gift = findGift(giftId);
        requireOwner(gift, userId);
        GiftCreateDto dto = new GiftCreateDto();
        dto.setId(gift.getId());
        dto.setTitle(gift.getTitle());
        dto.setDescription(gift.getDescription());
        dto.setLimit(gift.getLimit());
        return dto;
    }

    /**
     * Удаляет подарок вместе с резервами (только владелец).
     *
     * @param giftId идентификатор подарка
     * @param userId текущий пользователь
     */
    @Transactional
    public void deleteGift(Long giftId, Long userId) {
        Gift gift = findGift(giftId);
        requireOwner(gift, userId);
        reservationRepository.deleteByGiftId(giftId);
        giftRepository.delete(gift);
        log.info("Удалён подарок {} (ID: {})", gift.getTitle(), giftId);
    }

    /**
     * Обновляет подарок (только владелец).
     * <p>
     * Если новый лимит меньше текущего количества резервов, требуется подтверждение
     * ({@code confirmExcess}). При подтверждении отменяются самые последние резервы.
     *
     * @param giftId       идентификатор подарка
     * @param dto          новые данные
     * @param userId       текущий пользователь
     * @param confirmExcess подтверждение уменьшения лимита
     * @return обновлённый подарок
     */
    @Transactional
    public GiftDto updateGift(Long giftId, GiftCreateDto dto, Long userId, boolean confirmExcess) {
        Gift gift = findGift(giftId);
        requireOwner(gift, userId);
        if (dto.getLimit() > 0 && dto.getLimit() < gift.getReservedCount() && !confirmExcess) {
            throw new BusinessException(ErrorCode.LIMIT_TOO_LOW,
                    String.format("Новый лимит (%d) меньше текущего количества резервов (%d)",
                            dto.getLimit(), gift.getReservedCount()));
        }
        gift.setTitle(dto.getTitle().trim());
        gift.setDescription(dto.getDescription());
        gift.setLimit(dto.getLimit());
        if (dto.getLimit() > 0 && dto.getLimit() < gift.getReservedCount()) {
            cancelExcessReservations(gift, dto.getLimit());
        }
        Gift updated = giftRepository.save(gift);
        log.info("Обновлён подарок {} (ID: {})", updated.getTitle(), giftId);
        return toDto(updated, userId);
    }

    /**
     * Резервирует подарок (AJAX).
     *
     * @param giftId идентификатор подарка
     * @param userId пользователь, который резервирует
     * @return результат резервирования
     */
    @Transactional
    public ReservationResponseDto reserveGift(Long giftId, Long userId) {
        Gift gift = findGift(giftId);
        if (gift.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.CANNOT_RESERVE_OWN_GIFT);
        }
        if (reservationRepository.existsByGiftIdAndUserId(giftId, userId)) {
            throw new BusinessException(ErrorCode.ALREADY_RESERVED);
        }
        if (gift.getLimit() > 0 && gift.getReservedCount() >= gift.getLimit()) {
            throw new BusinessException(ErrorCode.LIMIT_REACHED);
        }
        int updated = giftRepository.incrementReservedCount(giftId);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.CONCURRENT_RESERVATION);
        }
        GiftReservation reservation = new GiftReservation();
        reservation.setGiftId(giftId);
        reservation.setUserId(userId);
        reservation.setReservedAt(LocalDateTime.now());
        reservationRepository.save(reservation);
        gift.setReservedCount(gift.getReservedCount() + 1);
        log.info("Пользователь {} зарезервировал подарок {} (ID: {})", userId, gift.getTitle(), giftId);
        eventPublisher.publishEvent(new GiftReservedEvent(giftId, userId, LocalDateTime.now()));
        return buildResponse(gift, true);
    }

    /**
     * Отменяет резерв пользователя (AJAX).
     *
     * @param giftId идентификатор подарка
     * @param userId пользователь, который отменяет резерв
     * @return результат отмены
     */
    @Transactional
    public ReservationResponseDto unreserveGift(Long giftId, Long userId) {
        Gift gift = findGift(giftId);
        GiftReservation reservation = reservationRepository.findByGiftIdAndUserId(giftId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_RESERVATION));
        reservationRepository.delete(reservation);
        giftRepository.decrementReservedCount(giftId);
        gift.setReservedCount(Math.max(0, gift.getReservedCount() - 1));
        log.info("Пользователь {} отменил резерв на подарок {} (ID: {})", userId, gift.getTitle(), giftId);
        return buildResponse(gift, false);
    }

    private void cancelExcessReservations(Gift gift, int newLimit) {
        List<GiftReservation> reservations = new ArrayList<>(reservationRepository.findByGiftId(gift.getId()));
        reservations.sort(Comparator.comparing(GiftReservation::getReservedAt).reversed());
        int toCancel = reservations.size() - newLimit;
        reservations.stream()
                .limit(Math.max(toCancel, 0))
                .forEach(reservation -> {
                    reservationRepository.delete(reservation);
                    log.info("Отменён резерв пользователя {} на подарок {} из-за уменьшения лимита",
                            reservation.getUserId(), gift.getTitle());
                });
        gift.setReservedCount(newLimit);
    }

    private GiftDto toDto(Gift gift, Long currentUserId) {
        GiftDto dto = new GiftDto();
        dto.setId(gift.getId());
        dto.setTitle(gift.getTitle());
        dto.setDescription(gift.getDescription());
        dto.setLimit(gift.getLimit());
        dto.setReservedCount(gift.getReservedCount());
        dto.setOwner(gift.getOwnerId().equals(currentUserId));
        dto.setReservedByMe(currentUserId != null
                && reservationRepository.existsByGiftIdAndUserId(gift.getId(), currentUserId));
        dto.setLimitReached(gift.getLimit() > 0 && gift.getReservedCount() >= gift.getLimit());
        dto.setReservedByUsers(loadReservedUsers(gift.getId()));
        return dto;
    }

    private List<UserDto> loadReservedUsers(Long giftId) {
        List<GiftReservation> reservations = reservationRepository.findByGiftId(giftId);
        if (reservations.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = reservations.stream()
                .map(GiftReservation::getUserId)
                .distinct()
                .toList();
        Map<Long, UserDto> byId = userRepository.findByIdIn(userIds).stream()
                .collect(Collectors.toMap(User::getId, userMapper::toDto, (a, b) -> a));
        return reservations.stream()
                .map(reservation -> byId.get(reservation.getUserId()))
                .filter(Objects::nonNull)
                .toList();
    }

    private ReservationResponseDto buildResponse(Gift gift, boolean reserved) {
        ReservationResponseDto response = new ReservationResponseDto();
        response.setSuccess(true);
        response.setNewReservedCount(gift.getReservedCount());
        response.setLimitReached(gift.getLimit() > 0 && gift.getReservedCount() >= gift.getLimit());
        response.setReservedByMe(reserved);
        if (reserved) {
            response.setButtonText("Отменить резерв");
            response.setButtonClass("btn-danger");
        } else {
            response.setButtonText("Зарезервировать");
            response.setButtonClass("btn-primary");
        }
        return response;
    }

    private Gift findGift(Long giftId) {
        return giftRepository.findById(giftId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GIFT_NOT_FOUND));
    }

    private void requireOwner(Gift gift, Long userId) {
        if (!gift.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }
    }
}