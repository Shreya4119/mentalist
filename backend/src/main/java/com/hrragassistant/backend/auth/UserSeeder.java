package com.hrragassistant.backend.auth;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Phase 2 uses two seeded demo accounts instead of a registration flow --
 * the point is proving RBAC gates the right endpoints, not building account
 * management. Credentials are documented in the README; dev-only.
 */
@Component
public class UserSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedIfMissing("hr_admin", "hr-admin-pass", Role.HR_ADMIN);
        seedIfMissing("employee", "employee-pass", Role.EMPLOYEE);
    }

    private void seedIfMissing(String username, String rawPassword, Role role) {
        if (userRepository.findByUsername(username).isPresent()) {
            return;
        }
        userRepository.save(new User(username, passwordEncoder.encode(rawPassword), role));
    }
}
