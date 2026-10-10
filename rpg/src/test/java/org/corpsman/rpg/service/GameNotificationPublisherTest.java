package org.corpsman.rpg.service;

import org.corpsman.auth.model.User;
import org.corpsman.auth.repository.UserRepository;
import org.corpsman.common.event.GameApplicationEvent;
import org.corpsman.common.event.GameCreatedEvent;
import org.corpsman.common.event.GameRoomPostCreatedEvent;
import org.corpsman.common.event.GameStatusChangedEvent;
import org.corpsman.rpg.model.ApplicationStatus;
import org.corpsman.rpg.model.Game;
import org.corpsman.rpg.model.GameApplication;
import org.corpsman.rpg.model.GameStatus;
import org.corpsman.rpg.model.Room;
import org.corpsman.rpg.model.RoomMember;
import org.corpsman.rpg.repository.GameApplicationRepository;
import org.corpsman.rpg.repository.RoomMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GameNotificationPublisherTest {

    @Mock
    private ApplicationEventPublisher events;
    @Mock
    private GameApplicationRepository applicationRepository;
    @Mock
    private RoomMemberRepository roomMemberRepository;
    @Mock
    private UserRepository userRepository;

    private GameNotificationPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new GameNotificationPublisher(events, applicationRepository, roomMemberRepository,
                userRepository);
    }

    private Game game() {
        Game game = new Game();
        game.setId(10L);
        game.setTitle("Ночь длинных клинков");
        game.setMasterId(1L);
        return game;
    }

    private GameApplication application(Long userId) {
        GameApplication app = new GameApplication();
        app.setId(100L + userId);
        app.setGameId(10L);
        app.setUserId(userId);
        return app;
    }

    private void stubUser(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
    }

    @Test
    void gameCreatedCarriesAuthorForFollowers() {
        stubUser(1L, "Мастер");

        publisher.gameCreated(game());

        ArgumentCaptor<GameCreatedEvent> captor = ArgumentCaptor.forClass(GameCreatedEvent.class);
        verify(events).publishEvent(captor.capture());
        GameCreatedEvent event = captor.getValue();
        assertThat(event.getUserId()).isEqualTo(1L);
        assertThat(event.getGameId()).isEqualTo(10L);
        assertThat(event.getGameTitle()).isEqualTo("Ночь длинных клинков");
        assertThat(event.getAuthorName()).isEqualTo("Мастер");
    }

    @Test
    void submittedApplicationNotifiesOnlyMaster() {
        stubUser(2L, "Игрок");
        GameApplication app = application(2L);

        publisher.applicationSubmitted(game(), app);

        ArgumentCaptor<GameApplicationEvent> captor = ArgumentCaptor.forClass(GameApplicationEvent.class);
        verify(events).publishEvent(captor.capture());
        GameApplicationEvent event = captor.getValue();
        assertThat(event.getAction()).isEqualTo("SUBMITTED");
        assertThat(event.getPlayerId()).isEqualTo(2L);
        assertThat(event.getPlayerName()).isEqualTo("Игрок");
        assertThat(event.getRecipientIds()).containsExactly(1L);
    }

    @Test
    void withdrawnApplicationNotifiesOnlyMaster() {
        stubUser(2L, "Игрок");

        publisher.applicationWithdrawn(game(), application(2L));

        ArgumentCaptor<GameApplicationEvent> captor = ArgumentCaptor.forClass(GameApplicationEvent.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo("WITHDRAWN");
        assertThat(captor.getValue().getRecipientIds()).containsExactly(1L);
    }

    @Test
    void reviewedApplicationNotifiesPlayerAboutResult() {
        stubUser(2L, "Игрок");

        publisher.applicationReviewed(game(), application(2L), true);

        ArgumentCaptor<GameApplicationEvent> captor = ArgumentCaptor.forClass(GameApplicationEvent.class);
        verify(events).publishEvent(captor.capture());
        GameApplicationEvent event = captor.getValue();
        assertThat(event.getAction()).isEqualTo("ACCEPTED");
        assertThat(event.isAccepted()).isTrue();
        assertThat(event.getRecipientIds()).containsExactly(2L);
    }

    @Test
    void rejectedApplicationIsSeparateAction() {
        stubUser(2L, "Игрок");

        publisher.applicationReviewed(game(), application(2L), false);

        ArgumentCaptor<GameApplicationEvent> captor = ArgumentCaptor.forClass(GameApplicationEvent.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo("REJECTED");
    }

    @Test
    void leftGameNotifiesMaster() {
        stubUser(2L, "Игрок");

        publisher.playerLeft(game(), application(2L));

        ArgumentCaptor<GameApplicationEvent> captor = ArgumentCaptor.forClass(GameApplicationEvent.class);
        verify(events).publishEvent(captor.capture());
        GameApplicationEvent event = captor.getValue();
        assertThat(event.getAction()).isEqualTo("LEFT");
        assertThat(event.getRecipientIds()).containsExactly(1L);
    }

    @Test
    void removedPlayerIsNotifiedAboutRemoval() {
        stubUser(2L, "Игрок");

        publisher.playerRemoved(game(), application(2L));

        ArgumentCaptor<GameApplicationEvent> captor = ArgumentCaptor.forClass(GameApplicationEvent.class);
        verify(events).publishEvent(captor.capture());
        GameApplicationEvent event = captor.getValue();
        assertThat(event.getAction()).isEqualTo("REMOVED");
        assertThat(event.getRecipientIds()).containsExactly(2L);
    }

    @Test
    void statusChangeNotifiesAcceptedPlayersOnly() {
        when(applicationRepository.findByGameIdAndStatusOrderByCreatedAtDesc(10L, ApplicationStatus.ACCEPTED))
                .thenReturn(List.of(application(2L), application(3L)));

        publisher.statusChanged(game(), GameStatus.RECRUITING, GameStatus.ACTIVE);

        ArgumentCaptor<GameStatusChangedEvent> captor = ArgumentCaptor.forClass(GameStatusChangedEvent.class);
        verify(events).publishEvent(captor.capture());
        GameStatusChangedEvent event = captor.getValue();
        assertThat(event.getOldStatusLabel()).isEqualTo("Набор игроков");
        assertThat(event.getNewStatusLabel()).isEqualTo("В процессе");
        assertThat(event.getRecipientIds()).containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    void playerPostNotifiesMasterAndOtherPlayersOfOpenRoom() {
        when(applicationRepository.findByGameIdAndStatusOrderByCreatedAtDesc(10L, ApplicationStatus.ACCEPTED))
                .thenReturn(List.of(application(2L), application(3L)));
        Room room = new Room();
        room.setId(55L);
        room.setGameId(10L);
        room.setTitle("Стол");
        room.setPrivateRoom(false);
        stubUser(2L, "игрок2");

        publisher.roomPostCreated(game(), room, 2L, false);

        ArgumentCaptor<GameRoomPostCreatedEvent> captor =
                ArgumentCaptor.forClass(GameRoomPostCreatedEvent.class);
        verify(events).publishEvent(captor.capture());
        GameRoomPostCreatedEvent event = captor.getValue();
        assertThat(event.getRoomId()).isEqualTo(55L);
        assertThat(event.getRoomTitle()).isEqualTo("Стол");
        assertThat(event.getAuthorName()).isEqualTo("игрок2");
        assertThat(event.getRecipientIds()).containsExactlyInAnyOrder(1L, 3L);
    }

    @Test
    void masterPostNotifiesPlayersOnly() {
        when(applicationRepository.findByGameIdAndStatusOrderByCreatedAtDesc(10L, ApplicationStatus.ACCEPTED))
                .thenReturn(List.of(application(2L)));
        Room room = new Room();
        room.setId(55L);
        room.setTitle("Стол");
        room.setPrivateRoom(false);

        publisher.roomPostCreated(game(), room, 1L, true);

        ArgumentCaptor<GameRoomPostCreatedEvent> captor =
                ArgumentCaptor.forClass(GameRoomPostCreatedEvent.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().getRecipientIds()).containsExactly(2L);
    }

    @Test
    void postInPrivateRoomSkipsNonMembers() {
        when(applicationRepository.findByGameIdAndStatusOrderByCreatedAtDesc(10L, ApplicationStatus.ACCEPTED))
                .thenReturn(List.of(application(2L), application(3L)));
        Room room = new Room();
        room.setId(55L);
        room.setTitle("Тайная комната");
        room.setPrivateRoom(true);
        RoomMember member = new RoomMember();
        member.setRoomId(55L);
        member.setUserId(3L);
        when(roomMemberRepository.findByRoomId(55L)).thenReturn(List.of(member));

        publisher.roomPostCreated(game(), room, 1L, true);

        ArgumentCaptor<GameRoomPostCreatedEvent> captor =
                ArgumentCaptor.forClass(GameRoomPostCreatedEvent.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().getRecipientIds()).containsExactly(3L);
    }

    @Test
    void authorOfPostIsNeverNotified() {
        when(applicationRepository.findByGameIdAndStatusOrderByCreatedAtDesc(10L, ApplicationStatus.ACCEPTED))
                .thenReturn(List.of(application(2L)));
        Room room = new Room();
        room.setId(55L);
        room.setTitle("Стол");
        room.setPrivateRoom(false);

        publisher.roomPostCreated(game(), room, 2L, false);

        ArgumentCaptor<GameRoomPostCreatedEvent> captor =
                ArgumentCaptor.forClass(GameRoomPostCreatedEvent.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().getRecipientIds()).containsExactly(1L);
        assertThat(captor.getValue().getRecipientIds()).doesNotContain(2L);
    }

    @Test
    void statusChangeOfGameWithoutPlayersIsNotPublished() {
        when(applicationRepository.findByGameIdAndStatusOrderByCreatedAtDesc(10L, ApplicationStatus.ACCEPTED))
                .thenReturn(List.of());

        publisher.statusChanged(game(), GameStatus.ACTIVE, GameStatus.CLOSED);

        verify(events, never()).publishEvent(any(GameStatusChangedEvent.class));
    }

    @Test
    void postInEmptyPrivateRoomIsNotPublished() {
        when(applicationRepository.findByGameIdAndStatusOrderByCreatedAtDesc(10L, ApplicationStatus.ACCEPTED))
                .thenReturn(List.of());
        Room room = new Room();
        room.setId(55L);
        room.setTitle("Тайная комната");
        room.setPrivateRoom(true);
        when(roomMemberRepository.findByRoomId(55L)).thenReturn(List.of());

        publisher.roomPostCreated(game(), room, 1L, true);

        verify(events, never()).publishEvent(any(GameRoomPostCreatedEvent.class));
    }
}
