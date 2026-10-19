package com.hrragassistant.backend.auth;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Two seeded demo accounts instead of a registration flow -- the point is
 * proving RBAC gates the right endpoints, not building account management.
 * Credentials are documented in the README; dev-only. Runs after
 * LegacyHrCleanup, which renames the old HR demo accounts first.
 */
@Component
@Order(10)
public class UserSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedIfMissing("admin", "admin-pass", Role.ADMIN);
        seedIfMissing("demo", "demo-pass", Role.USER);
    }

    private void seedIfMissing(String username, String rawPassword, Role role) {
        if (userRepository.findByUsername(username).isPresent()) {
            return;
        }
        userRepository.save(new User(username, passwordEncoder.encode(rawPassword), role));
    }
}
