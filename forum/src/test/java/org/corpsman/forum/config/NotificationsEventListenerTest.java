package org.corpsman.forum.config;

import org.corpsman.auth.repository.UserRepository;
import org.corpsman.common.event.GameApplicationEvent;
import org.corpsman.common.event.GameCreatedEvent;
import org.corpsman.common.event.GameRoomPostCreatedEvent;
import org.corpsman.common.event.GameStatusChangedEvent;
import org.corpsman.forum.model.Subscription;
import org.corpsman.forum.repository.SubscriptionRepository;
import org.corpsman.forum.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationsEventListenerTest {

    @Mock
    private NotificationService notificationService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SubscriptionRepository subscriptionRepository;

    private NotificationsEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new NotificationsEventListener(notificationService, userRepository, subscriptionRepository);
    }

    /**
     * Проверяет уведомление списку получателей.
     */
    private void assertNotified(List<Long> recipients, String type, String text, String url) {
        ArgumentCaptor<List<Long>> users = ArgumentCaptor.forClass(List.class);
        verify(notificationService).notify(users.capture(), eq(type), eq(text), eq(url));
        assertThat(users.getValue()).isEqualTo(recipients);
    }

    private Subscription subscription(Long subscriberId, Long targetUserId) {
        Subscription s = new Subscription();
        s.setSubscriberId(subscriberId);
        s.setTargetUserId(targetUserId);
        return s;
    }

    @Test
    void newGameNotifiesFollowersExceptAuthor() {
        when(subscriptionRepository.findByTargetUserId(1L))
                .thenReturn(List.of(subscription(2L, 1L), subscription(3L, 1L), subscription(1L, 1L)));

        listener.onGameCreated(new GameCreatedEvent(10L, "Ночь длинных клинков", "Мастер", 1L,
                LocalDateTime.now()));

        ArgumentCaptor<Long> users = ArgumentCaptor.forClass(Long.class);
        verify(notificationService, org.mockito.Mockito.times(2)).notify(users.capture(),
                org.mockito.ArgumentMatchers.eq("RPG_GAME_CREATED"), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("/rpg/games/10"));
        assertThat(users.getAllValues()).containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    void submittedApplicationTextNamesPlayerAndGame() {
        listener.onGameApplication(new GameApplicationEvent(10L, "Ночь длинных клинков", 100L, 2L, "Игрок",
                "SUBMITTED", true, List.of(1L), 2L, LocalDateTime.now()));

        assertNotified(List.of(1L), "RPG_APPLICATION_SUBMITTED",
                "Игрок подал заявку в игру «Ночь длинных клинков»", "/rpg/games/10");
    }

    @Test
    void withdrawnApplicationNotified() {
        listener.onGameApplication(new GameApplicationEvent(10L, "Ночь", 100L, 2L, "Игрок",
                "WITHDRAWN", true, List.of(1L), 2L, LocalDateTime.now()));

        assertNotified(List.of(1L), "RPG_APPLICATION_WITHDRAWN",
                "Игрок отозвал заявку из игры «Ночь»", "/rpg/games/10");
    }

    @Test
    void acceptedApplicationNotified() {
        listener.onGameApplication(new GameApplicationEvent(10L, "Ночь", 100L, 2L, "Игрок",
                "ACCEPTED", true, List.of(2L), 1L, LocalDateTime.now()));

        assertNotified(List.of(2L), "RPG_APPLICATION_ACCEPTED",
                "Ваша заявка в игру «Ночь» принята", "/rpg/games/10");
    }

    @Test
    void rejectedApplicationNotified() {
        listener.onGameApplication(new GameApplicationEvent(10L, "Ночь", 100L, 2L, "Игрок",
                "REJECTED", false, List.of(2L), 1L, LocalDateTime.now()));

        assertNotified(List.of(2L), "RPG_APPLICATION_REJECTED",
                "Ваша заявка в игру «Ночь» отклонена", "/rpg/games/10");
    }

    @Test
    void leftGameNotifiedToMaster() {
        listener.onGameApplication(new GameApplicationEvent(10L, "Ночь", 100L, 2L, "Игрок",
                "LEFT", true, List.of(1L), 2L, LocalDateTime.now()));

        assertNotified(List.of(1L), "RPG_GAME_PLAYER_LEFT",
                "Игрок покинул игру «Ночь»", "/rpg/games/10");
    }

    @Test
    void removalNotifiedToPlayer() {
        listener.onGameApplication(new GameApplicationEvent(10L, "Ночь", 100L, 2L, "Игрок",
                "REMOVED", true, List.of(2L), 1L, LocalDateTime.now()));

        assertNotified(List.of(2L), "RPG_GAME_PLAYER_REMOVED",
                "Вы были удалены из игры «Ночь»", "/rpg/games/10");
    }

    @Test
    void statusChangeUsesStatusLabels() {
        listener.onGameStatusChanged(new GameStatusChangedEvent(10L, "Ночь", "Набор игроков",
                "В процессе", List.of(2L, 3L), 1L, LocalDateTime.now()));

        ArgumentCaptor<List<Long>> users = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> texts = ArgumentCaptor.forClass(String.class);
        verify(notificationService).notify(users.capture(), eq("RPG_GAME_STATUS_CHANGED"), texts.capture(),
                eq("/rpg/games/10"));
        assertThat(users.getValue()).containsExactlyInAnyOrder(2L, 3L);
        assertThat(texts.getValue()).contains("Набор игроков").contains("В процессе");
    }

    @Test
    void roomPostLinksToRoom() {
        listener.onRoomPostCreated(new GameRoomPostCreatedEvent(10L, "Ночь", 55L, "Стол", "Игрок",
                false, List.of(2L), 3L, LocalDateTime.now()));

        assertNotified(List.of(2L), "RPG_ROOM_POST",
                "Новый пост в комнате «Стол» игры «Ночь» от Игрок", "/rpg/games/10/rooms/55");
    }

    @Test
    void emptyRecipientListIsPassedToServiceAsIs() {
        // Пустой список получателей обрабатывает сам сервис уведомлений.
        listener.onGameStatusChanged(new GameStatusChangedEvent(10L, "Ночь", "В процессе",
                "Завершена", List.of(), 1L, LocalDateTime.now()));

        verify(notificationService).notify(eq(List.of()), eq("RPG_GAME_STATUS_CHANGED"),
                eq("Игра «Ночь» изменила статус: В процессе → Завершена"), eq("/rpg/games/10"));
    }
}
