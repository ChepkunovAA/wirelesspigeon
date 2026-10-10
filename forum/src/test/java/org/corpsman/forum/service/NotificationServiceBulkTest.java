package org.corpsman.forum.service;

import org.corpsman.forum.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationServiceBulkTest {

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository, null);
    }

    @Test
    void bulkNotifyCreatesOneNotificationPerRecipient() {
        service.notify(List.of(2L, 3L, 4L), "RPG_GAME_CREATED", "Новая игра", "/rpg/games/1");

        ArgumentCaptor<org.corpsman.forum.model.Notification> captor =
                ArgumentCaptor.forClass(org.corpsman.forum.model.Notification.class);
        verify(notificationRepository, org.mockito.Mockito.times(3)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(n -> n.getUserId()).containsExactly(2L, 3L, 4L);
        assertThat(captor.getAllValues()).allMatch(n -> n.getType().equals("RPG_GAME_CREATED")
                && n.getUrl().equals("/rpg/games/1") && !n.isRead());
    }

    @Test
    void emptyRecipientListCreatesNothing() {
        service.notify(List.of(), "RPG_GAME_CREATED", "Новая игра", "/rpg/games/1");
        service.notify((List<Long>) null, "RPG_GAME_CREATED", "Новая игра", "/rpg/games/1");

        verify(notificationRepository, never()).save(any());
    }
}
