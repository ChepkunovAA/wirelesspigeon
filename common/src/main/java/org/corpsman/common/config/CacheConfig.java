package org.corpsman.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Конфигурация кеша Caffeine.
 * <p>
 * Кеш в памяти приложения, при необходимости заменяем на Redis без изменения
 * сервисного кода (через {@link CacheManager}).
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Создаёт менеджер кеша Caffeine с настройками по умолчанию.
     *
     * @return менеджер кеша
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
                "films", "tags", "cinemaDashboard", "filmStats");
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(200)
                .expireAfterWrite(Duration.ofMinutes(10)));
        return manager;
    }
}