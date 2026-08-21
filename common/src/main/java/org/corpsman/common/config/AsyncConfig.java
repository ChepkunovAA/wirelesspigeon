package org.corpsman.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Конфигурация асинхронной обработки внутренних событий.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Создаёт пул потоков для обработчиков доменных событий.
     *
     * @return исполнитель асинхронных задач
     */
    @Bean(name = "corpsmanTaskExecutor")
    public Executor corpsmanTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("corpsman-async-");
        executor.initialize();
        return executor;
    }
}