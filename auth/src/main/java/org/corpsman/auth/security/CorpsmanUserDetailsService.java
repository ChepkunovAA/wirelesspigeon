package org.corpsman.auth.security;

import lombok.RequiredArgsConstructor;
import org.corpsman.auth.model.User;
import org.corpsman.auth.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Загрузчик пользователей Spring Security из репозитория.
 */
@Service
@RequiredArgsConstructor
public class CorpsmanUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден: " + username));
        return new CorpsmanUserDetails(user);
    }
}