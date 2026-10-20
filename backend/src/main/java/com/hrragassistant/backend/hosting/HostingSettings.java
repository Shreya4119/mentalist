package com.hrragassistant.backend.hosting;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * How this copy of Mentalist is hosted. Locally everything is off and the app
 * behaves as before. On the public server (PUBLIC_MODE=true): people create
 * their own accounts, the development secrets are refused, and the backend
 * trusts the proxy's X-Forwarded-For for rate limiting.
 */
@Component
public class HostingSettings {

    private final boolean publicMode;
    private final boolean signupEnabled;
    private final int maxUsers;
    private final boolean trustForwardedFor;

    public HostingSettings(@Value("${app.hosting.public-mode:false}") boolean publicMode,
                           @Value("${app.hosting.signup-enabled:false}") boolean signupEnabled,
                           @Value("${app.hosting.max-users:1000}") int maxUsers,
                           @Value("${app.security.trust-forwarded-for:false}") boolean trustForwardedFor) {
        this.publicMode = publicMode;
        this.signupEnabled = signupEnabled;
        this.maxUsers = maxUsers;
        this.trustForwardedFor = trustForwardedFor;
    }

    public boolean publicMode() {
        return publicMode;
    }

    public boolean signupEnabled() {
        return signupEnabled;
    }

    /** Sign-ups stop at this many accounts (0 = no cap), so a script can't fill the database. */
    public int maxUsers() {
        return maxUsers;
    }

    public boolean trustForwardedFor() {
        return trustForwardedFor;
    }
}
