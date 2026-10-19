package com.hrragassistant.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * One-time, idempotent cleanup after the HR documents feature was removed.
 * Runs before UserSeeder on every start and does nothing once applied:
 *
 * 1. Roles HR_ADMIN/EMPLOYEE become ADMIN/USER (the old enum check
 *    constraint Hibernate created is dropped first).
 * 2. The old demo accounts hr_admin/employee become admin/demo, with the
 *    passwords documented in the README, and keep the reports they uploaded.
 * 3. HR policy documents (kind POLICY) and their vector chunks are deleted.
 *
 * Each step is independent and only logs on failure (e.g. on a fresh
 * database where a table does not exist yet). Replace with a Flyway
 * migration when migrations are introduced.
 */
@Component
@Order(0)
public class LegacyHrCleanup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LegacyHrCleanup.class);

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    public LegacyHrCleanup(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        step("drop old role constraint", () -> {
            jdbc.execute("ALTER TABLE app_user DROP CONSTRAINT IF EXISTS app_user_role_check");
            return null;
        });
        step("rename roles", () -> {
            int n = jdbc.update("UPDATE app_user SET role = 'ADMIN' WHERE role = 'HR_ADMIN'")
                    + jdbc.update("UPDATE app_user SET role = 'USER' WHERE role = 'EMPLOYEE'");
            return n > 0 ? "renamed the role of " + n + " user(s)" : null;
        });
        step("rename demo accounts", () -> {
            String a = renameAccount("hr_admin", "admin", "admin-pass");
            String b = renameAccount("employee", "demo", "demo-pass");
            return a == null && b == null ? null : (a == null ? "" : a) + (b == null ? "" : " " + b);
        });
        step("delete HR documents", () -> {
            int chunks = jdbc.update("DELETE FROM vector_store WHERE metadata->>'document_id' IN "
                    + "(SELECT CAST(id AS text) FROM hr_document WHERE kind = 'POLICY')");
            int docs = jdbc.update("DELETE FROM hr_document WHERE kind = 'POLICY'");
            return docs > 0 ? "deleted " + docs + " HR document(s) and " + chunks + " indexed chunk(s)" : null;
        });
        // Report files now live on disk; new rows leave the old bytea column empty.
        step("allow file-backed documents", () -> {
            jdbc.execute("ALTER TABLE hr_document ALTER COLUMN content DROP NOT NULL");
            return null;
        });
        step("update document kind default", () -> {
            jdbc.execute("ALTER TABLE hr_document ALTER COLUMN kind SET DEFAULT 'FINANCIAL_REPORT'");
            return null;
        });
    }

    private String renameAccount(String oldName, String newName, String newPassword) {
        Integer clash = jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE username = ?", Integer.class, newName);
        if (clash != null && clash > 0) {
            return null;
        }
        int n = jdbc.update("UPDATE app_user SET username = ?, password_hash = ? WHERE username = ?",
                newName, passwordEncoder.encode(newPassword), oldName);
        if (n == 0) {
            return null;
        }
        jdbc.update("UPDATE financial_report_analysis SET uploaded_by = ? WHERE uploaded_by = ?", newName, oldName);
        return "renamed account " + oldName + " to " + newName + ".";
    }

    private void step(String name, java.util.function.Supplier<String> action) {
        try {
            String result = action.get();
            if (result != null && !result.isBlank()) {
                log.info("Legacy HR cleanup: {}", result.trim());
            }
        } catch (Exception e) {
            log.debug("Legacy HR cleanup step '{}' skipped: {}", name, e.getMessage());
        }
    }
}
