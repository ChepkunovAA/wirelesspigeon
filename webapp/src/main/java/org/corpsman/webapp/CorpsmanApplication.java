package org.corpsman.webapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Точка входа веб-приложения Corpsman.
 */
@SpringBootApplication(scanBasePackages = "org.corpsman")
@EntityScan(basePackages = "org.corpsman")
@EnableJpaRepositories(basePackages = "org.corpsman")
public class CorpsmanApplication {

    public static void main(String[] args) {
        SpringApplication.run(CorpsmanApplication.class, args);
    }
}