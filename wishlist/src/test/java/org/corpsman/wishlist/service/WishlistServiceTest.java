package org.corpsman.wishlist.service;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WishlistServiceTest {

    @Mock
    private GiftRepository giftRepository;

    @Mock
    private GiftReservationRepository reservationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private WishlistService wishlistService;

    private Gift gift(long id, long ownerId, int limit, int reservedCount) {
        Gift gift = new Gift();
        gift.setId(id);
        gift.setTitle("Подарок");
        gift.setDescription("Описание");
        gift.setOwnerId(ownerId);
        gift.setLimit(limit);
        gift.setReservedCount(reservedCount);
        return gift;
    }

    private GiftCreateDto dto(String title, int limit) {
        GiftCreateDto dto = new GiftCreateDto();
        dto.setTitle(title);
        dto.setDescription("Описание");
        dto.setLimit(limit);
        return dto;
    }

    @Test
    void addGiftSavesTrimmedTitleWithDefaultLimit() {
        GiftCreateDto dto = dto("  Книга  ", 0);
        when(giftRepository.save(any(Gift.class))).thenAnswer(invocation -> {
            Gift saved = invocation.getArgument(0);
            saved.setId(5L);
            return saved;
        });

        GiftDto result = wishlistService.addGift(dto, 1L);

        ArgumentCaptor<Gift> captor = ArgumentCaptor.forClass(Gift.class);
        verify(giftRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("Книга");
        assertThat(captor.getValue().getLimit()).isZero();
        assertThat(captor.getValue().getOwnerId()).isEqualTo(1L);
        assertThat(result.getId()).isEqualTo(5L);
    }

    @Test
    void getUserGiftsSetsViewerFlags() {
        Gift gift = gift(1L, 1L, 2, 1);
        GiftReservation reservation = new GiftReservation();
        reservation.setGiftId(1L);
        reservation.setUserId(2L);
        reservation.setReservedAt(LocalDateTime.now());
        User user = new User();
        user.setId(2L);
        user.setUsername("alice");
        UserDto userDto = new UserDto();
        userDto.setId(2L);
        userDto.setUsername("alice");
        when(giftRepository.findByOwnerIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(gift));
        when(reservationRepository.existsByGiftIdAndUserId(1L, 2L)).thenReturn(true);
        when(reservationRepository.findByGiftId(1L)).thenReturn(List.of(reservation));
        when(userRepository.findByIdIn(any())).thenReturn(List.of(user));
        when(userMapper.toDto(user)).thenReturn(userDto);

        List<GiftDto> gifts = wishlistService.getUserGifts(1L, 2L);

        assertThat(gifts).hasSize(1);
        GiftDto dto = gifts.get(0);
        assertThat(dto.isOwner()).isFalse();
        assertThat(dto.isReservedByMe()).isTrue();
        assertThat(dto.isLimitReached()).isFalse();
        assertThat(dto.getReservedByUsers()).extracting(UserDto::getUsername).containsExactly("alice");
    }

    @Test
    void getUserGiftsMarksLimitReached() {
        Gift gift = gift(1L, 1L, 2, 2);
        when(giftRepository.findByOwnerIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(gift));
        when(reservationRepository.existsByGiftIdAndUserId(1L, 1L)).thenReturn(false);

        List<GiftDto> gifts = wishlistService.getUserGifts(1L, 1L);

        assertThat(gifts.get(0).isLimitReached()).isTrue();
        assertThat(gifts.get(0).isOwner()).isTrue();
    }

    @Test
    void deleteGiftByOwnerCascadesReservations() {
        Gift gift = gift(1L, 1L, 2, 1);
        when(giftRepository.findById(1L)).thenReturn(Optional.of(gift));

        wishlistService.deleteGift(1L, 1L);

        verify(reservationRepository).deleteByGiftId(1L);
        verify(giftRepository).delete(gift);
    }

    @Test
    void deleteGiftByNonOwnerIsForbidden() {
        Gift gift = gift(1L, 1L, 2, 0);
        when(giftRepository.findById(1L)).thenReturn(Optional.of(gift));

        assertThatThrownBy(() -> wishlistService.deleteGift(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PERMISSION_DENIED);
        verify(giftRepository, never()).delete(any());
    }

    @Test
    void updateGiftRejectsLowerLimitWithoutConfirmation() {
        Gift gift = gift(1L, 1L, 3, 3);
        when(giftRepository.findById(1L)).thenReturn(Optional.of(gift));

        assertThatThrownBy(() -> wishlistService.updateGift(1L, dto("Книга", 1), 1L, false))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LIMIT_TOO_LOW);
        verify(giftRepository, never()).save(any());
    }

    @Test
    void updateGiftWithConfirmationCancelsLatestReservations() {
        Gift gift = gift(1L, 1L, 3, 3);
        GiftReservation older = new GiftReservation();
        older.setGiftId(1L);
        older.setUserId(2L);
        older.setReservedAt(LocalDateTime.now().minusMinutes(10));
        GiftReservation newer = new GiftReservation();
        newer.setGiftId(1L);
        newer.setUserId(3L);
        newer.setReservedAt(LocalDateTime.now());
        when(reservationRepository.findByGiftId(1L)).thenReturn(List.of(older, newer));
        when(giftRepository.findById(1L)).thenReturn(Optional.of(gift));
        when(giftRepository.save(any(Gift.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reservationRepository.existsByGiftIdAndUserId(1L, 1L)).thenReturn(false);

        GiftDto result = wishlistService.updateGift(1L, dto("Книга", 1), 1L, true);

        verify(reservationRepository).delete(newer);
        verify(reservationRepository, never()).delete(older);
        assertThat(gift.getReservedCount()).isEqualTo(1);
        assertThat(result.getTitle()).isEqualTo("Книга");
    }

    @Test
    void reserveGiftSucceedsAndPublishesEvent() {
        Gift gift = gift(1L, 1L, 5, 0);
        when(giftRepository.findById(1L)).thenReturn(Optional.of(gift));
        when(reservationRepository.existsByGiftIdAndUserId(1L, 2L)).thenReturn(false);
        when(giftRepository.incrementReservedCount(1L)).thenReturn(1);

        ReservationResponseDto response = wishlistService.reserveGift(1L, 2L);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getNewReservedCount()).isEqualTo(1);
        assertThat(response.isReservedByMe()).isTrue();
        assertThat(response.getButtonText()).isEqualTo("Отменить резерв");
        ArgumentCaptor<GiftReservation> captor = ArgumentCaptor.forClass(GiftReservation.class);
        verify(reservationRepository).save(captor.capture());
        assertThat(captor.getValue().getGiftId()).isEqualTo(1L);
        assertThat(captor.getValue().getUserId()).isEqualTo(2L);
        verify(eventPublisher).publishEvent(any(GiftReservedEvent.class));
    }

    @Test
    void cannotReserveOwnGift() {
        Gift gift = gift(1L, 1L, 5, 0);
        when(giftRepository.findById(1L)).thenReturn(Optional.of(gift));

        assertThatThrownBy(() -> wishlistService.reserveGift(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CANNOT_RESERVE_OWN_GIFT);
        verify(giftRepository, never()).incrementReservedCount(anyLong());
    }

    @Test
    void cannotReserveTwice() {
        Gift gift = gift(1L, 1L, 5, 1);
        when(giftRepository.findById(1L)).thenReturn(Optional.of(gift));
        when(reservationRepository.existsByGiftIdAndUserId(1L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> wishlistService.reserveGift(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ALREADY_RESERVED);
    }

    @Test
    void cannotReserveWhenLimitReached() {
        Gift gift = gift(1L, 1L, 2, 2);
        when(giftRepository.findById(1L)).thenReturn(Optional.of(gift));
        when(reservationRepository.existsByGiftIdAndUserId(1L, 2L)).thenReturn(false);

        assertThatThrownBy(() -> wishlistService.reserveGift(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LIMIT_REACHED);
    }

    @Test
    void concurrentReservationIsRejected() {
        Gift gift = gift(1L, 1L, 2, 1);
        when(giftRepository.findById(1L)).thenReturn(Optional.of(gift));
        when(reservationRepository.existsByGiftIdAndUserId(1L, 2L)).thenReturn(false);
        when(giftRepository.incrementReservedCount(1L)).thenReturn(0);

        assertThatThrownBy(() -> wishlistService.reserveGift(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONCURRENT_RESERVATION);
        verify(reservationRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void unreserveGiftDeletesReservationAndDecrements() {
        Gift gift = gift(1L, 1L, 5, 1);
        GiftReservation reservation = new GiftReservation();
        reservation.setGiftId(1L);
        reservation.setUserId(2L);
        reservation.setReservedAt(LocalDateTime.now());
        when(giftRepository.findById(1L)).thenReturn(Optional.of(gift));
        when(reservationRepository.findByGiftIdAndUserId(1L, 2L)).thenReturn(Optional.of(reservation));

        ReservationResponseDto response = wishlistService.unreserveGift(1L, 2L);

        verify(reservationRepository).delete(reservation);
        verify(giftRepository).decrementReservedCount(1L);
        assertThat(response.isReservedByMe()).isFalse();
        assertThat(response.getButtonText()).isEqualTo("Зарезервировать");
    }

    @Test
    void unreserveWithoutReservationFails() {
        Gift gift = gift(1L, 1L, 5, 1);
        when(giftRepository.findById(1L)).thenReturn(Optional.of(gift));
        when(reservationRepository.findByGiftIdAndUserId(1L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> wishlistService.unreserveGift(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NO_RESERVATION);
    }

    @Test
    void unknownGiftNotFound() {
        when(giftRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> wishlistService.deleteGift(99L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.GIFT_NOT_FOUND);
        verify(giftRepository, never()).delete(any());
    }
}