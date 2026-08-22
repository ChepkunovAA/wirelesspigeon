package org.corpsman.auth.config;

import org.corpsman.auth.security.CorpsmanUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Конфигурация Spring Security.
 * <p>
 * Session-based аутентификация, CSRF включён, все страницы требуют авторизации,
 * кроме /login и /register. Раздел /admin/** доступен только роли ADMIN.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Кодировщик паролей BCrypt со strength 12.
     *
     * @return кодировщик паролей
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Провайдер аутентификации на основе UserDetailsService и PasswordEncoder.
     *
     * @param userDetailsService загрузчик пользователей
     * @param passwordEncoder    кодировщик паролей
     * @return провайдер аутентификации
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider(
            CorpsmanUserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    /**
     * Менеджер аутентификации для ручного входа в AuthController.
     *
     * @param configuration конфигурация аутентификации
     * @return менеджер аутентификации
     * @throws Exception если менеджер не удалось собрать
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /**
     * Цепочка фильтров безопасности.
     *
     * @param http объект конфигурации HTTP-безопасности
     * @return цепочка фильтров
     * @throws Exception если цепочку не удалось собрать
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/login", "/register", "/error", "/static/**", "/css/**", "/js/**", "/images/**", "/assets/**", "/public/**", "/forum/public/**").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/users/*/ban").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/users/*/reset-password").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .csrf(Customizer.withDefaults())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) ->
                        response.sendRedirect("/login")))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/"))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));
        return http.build();
    }
}