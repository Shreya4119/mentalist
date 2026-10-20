package com.hrragassistant.backend.hosting;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in person's daily allowance, and deleting their account. */
@RestController
public class LimitsController {

    private final UsageLimits limits;
    private final AccountDeletion deletion;

    public LimitsController(UsageLimits limits, AccountDeletion deletion) {
        this.limits = limits;
        this.deletion = deletion;
    }

    @GetMapping("/api/limits")
    public UsageLimits.Remaining limits(Authentication auth) {
        return limits.remaining(auth.getName(), isAdmin(auth));
    }

    /** Deletes the account with everything it owns: uploaded reports and their files, and saved health checks. */
    @DeleteMapping("/api/account")
    public ResponseEntity<Void> deleteAccount(Authentication auth) {
        if (isAdmin(auth)) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN,
                    "The admin account can't be deleted from the app.");
        }
        deletion.delete(auth.getName());
        return ResponseEntity.noContent().build();
    }

    private static boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
