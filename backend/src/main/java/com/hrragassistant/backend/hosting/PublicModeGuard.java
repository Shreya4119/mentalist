package com.hrragassistant.backend.hosting;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Refuses to start a public server with the development secrets that are in
 * the public GitHub repository (JWT secret, admin and database passwords).
 */
@Component
@Order(1)
public class PublicModeGuard implements ApplicationRunner {

    private final HostingSettings hosting;
    private final String jwtSecret;
    private final String adminPassword;
    private final String dbPassword;

    public PublicModeGuard(HostingSettings hosting,
                           @Value("${app.jwt.secret}") String jwtSecret,
                           @Value("${app.seed.admin-password:admin-pass}") String adminPassword,
                           @Value("${spring.datasource.password:}") String dbPassword) {
        this.hosting = hosting;
        this.jwtSecret = jwtSecret;
        this.adminPassword = adminPassword;
        this.dbPassword = dbPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> problems = problems(hosting.publicMode(), jwtSecret, adminPassword, dbPassword);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("PUBLIC_MODE is on but the server still uses development settings: "
                    + String.join("; ", problems) + ". Set them in the server's .env file (see deploy/PUBLIC_SERVER.md).");
        }
    }

    static List<String> problems(boolean publicMode, String jwtSecret, String adminPassword, String dbPassword) {
        List<String> problems = new ArrayList<>();
        if (!publicMode) {
            return problems;
        }
        if (jwtSecret == null || jwtSecret.contains("dev-only") || jwtSecret.length() < 32) {
            problems.add("JWT_SECRET must be a random value of at least 32 characters");
        }
        if (adminPassword == null || adminPassword.equals("admin-pass") || adminPassword.length() < 12) {
            problems.add("ADMIN_PASSWORD must be set (at least 12 characters)");
        }
        if (dbPassword == null || dbPassword.equals("hrrag") || dbPassword.length() < 12) {
            problems.add("POSTGRES_PASSWORD must be set (at least 12 characters)");
        }
        return problems;
    }
}
