package org.corpsman.auth.config;

import lombok.extern.slf4j.Slf4j;
import org.corpsman.common.event.UserRegisteredEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Подписчик на доменные события модуля auth.
 */
@Component
@Slf4j
public class AuthEventListener {

    /**
     * Обрабатывает событие регистрации пользователя.
     *
     * @param event событие регистрации
     */
    @Async("corpsmanTaskExecutor")
    @EventListener
    public void onUserRegistered(UserRegisteredEvent event) {
        log.debug("Обработка события регистрации, userId={}, username={}", event.getUserId(), event.getUsername());
    }
}