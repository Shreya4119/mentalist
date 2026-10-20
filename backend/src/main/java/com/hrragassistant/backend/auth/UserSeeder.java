package com.hrragassistant.backend.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.hrragassistant.backend.hosting.HostingSettings;

/**
 * Seeded accounts. Locally: admin/admin-pass and demo/demo-pass (documented
 * in the README). On the public server the admin password comes from
 * ADMIN_PASSWORD (a changed value is applied on the next start), and the
 * well-known demo account is not created: people sign up instead. Runs after
 * LegacyHrCleanup, which renames the old HR demo accounts first.
 */
@Component
@Order(10)
public class UserSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final HostingSettings hosting;
    private final String adminPassword;
    private final String demoPassword;

    public UserSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder, HostingSettings hosting,
                      @Value("${app.seed.admin-password:admin-pass}") String adminPassword,
                      @Value("${app.seed.demo-password:demo-pass}") String demoPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.hosting = hosting;
        this.adminPassword = adminPassword;
        this.demoPassword = demoPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed("admin", adminPassword, Role.ADMIN);
        if (!hosting.publicMode()) {
            seed("demo", demoPassword, Role.USER);
        } else {
            // Its password is public on GitHub; nobody should be able to sign in with it.
            userRepository.findByUsername("demo").ifPresent(userRepository::delete);
        }
    }

    private void seed(String username, String rawPassword, Role role) {
        userRepository.findByUsername(username).ifPresentOrElse(user -> {
            if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
                user.setPasswordHash(passwordEncoder.encode(rawPassword));
                userRepository.save(user);
            }
        }, () -> userRepository.save(new User(username, passwordEncoder.encode(rawPassword), role)));
    }
}
