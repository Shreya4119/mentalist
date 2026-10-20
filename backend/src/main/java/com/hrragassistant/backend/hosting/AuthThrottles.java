package com.hrragassistant.backend.hosting;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Per-address limits on failed sign-ins and on new accounts. */
@Component
public class AuthThrottles {

    private final SlidingWindowLimiter failedLogins;
    private final SlidingWindowLimiter signups;

    public AuthThrottles(@Value("${app.security.max-failed-logins:10}") int maxFailedLogins,
                         @Value("${app.security.failed-login-window-minutes:15}") int failedLoginWindow,
                         @Value("${app.hosting.signups-per-hour:5}") int signupsPerHour) {
        Clock clock = Clock.systemUTC();
        this.failedLogins = new SlidingWindowLimiter(maxFailedLogins, Duration.ofMinutes(failedLoginWindow), clock);
        this.signups = new SlidingWindowLimiter(signupsPerHour, Duration.ofHours(1), clock);
    }

    public void checkLogin(String address) {
        if (!failedLogins.allowed(address)) {
            long minutes = Math.max(1, (failedLogins.retryAfterSeconds(address) + 59) / 60);
            throw new LimitReached("Too many failed sign-in attempts. Try again in " + minutes + " minute"
                    + (minutes == 1 ? "" : "s") + ".", failedLogins.retryAfterSeconds(address));
        }
    }

    public void loginFailed(String address) {
        failedLogins.record(address);
    }

    public void loginSucceeded(String address) {
        failedLogins.reset(address);
    }

    public void acquireSignup(String address) {
        if (!signups.tryAcquire(address)) {
            throw new LimitReached("Too many new accounts from your network in the last hour. Please try again later.",
                    signups.retryAfterSeconds(address));
        }
    }
}
