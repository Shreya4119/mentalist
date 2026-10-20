package com.hrragassistant.backend.hosting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class HostingLimitsTest {

    /** A clock the test can move forward. */
    static final class TestClock extends Clock {
        Instant now;
        final ZoneId zone;

        TestClock(Instant start, ZoneId zone) {
            this.now = start;
            this.zone = zone;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override public ZoneId getZone() { return zone; }
        @Override public Clock withZone(ZoneId z) { return new TestClock(now, z); }
        @Override public Instant instant() { return now; }
    }

    @Test
    void dailyQuotaStopsAtTheLimitAndResetsAtMidnight() {
        TestClock clock = new TestClock(Instant.parse("2026-10-20T21:00:00Z"), ZoneId.of("Europe/Amsterdam"));
        DailyQuota quota = new DailyQuota(clock);
        assertThat(quota.tryAcquire("a", 2)).isTrue();
        assertThat(quota.tryAcquire("a", 2)).isTrue();
        assertThat(quota.tryAcquire("a", 2)).isFalse();
        assertThat(quota.remaining("a", 2)).isZero();
        quota.release("a");
        assertThat(quota.tryAcquire("a", 2)).isTrue();
        clock.advance(Duration.ofHours(2)); // 01:00 in Amsterdam: a new day
        assertThat(quota.used("a")).isZero();
    }

    @Test
    void zeroMeansUnlimited() {
        DailyQuota quota = new DailyQuota(Clock.systemUTC());
        for (int i = 0; i < 1000; i++) {
            assertThat(quota.tryAcquire("a", 0)).isTrue();
        }
        assertThat(quota.remaining("a", 0)).isEqualTo(-1);
    }

    @Test
    void slidingWindowForgetsOldEvents() {
        TestClock clock = new TestClock(Instant.parse("2026-10-20T10:00:00Z"), ZoneOffset.UTC);
        SlidingWindowLimiter limiter = new SlidingWindowLimiter(3, Duration.ofMinutes(15), clock);
        assertThat(limiter.tryAcquire("ip")).isTrue();
        clock.advance(Duration.ofMinutes(5));
        assertThat(limiter.tryAcquire("ip")).isTrue();
        assertThat(limiter.tryAcquire("ip")).isTrue();
        assertThat(limiter.tryAcquire("ip")).isFalse();
        assertThat(limiter.allowed("other")).isTrue();
        assertThat(limiter.retryAfterSeconds("ip")).isEqualTo(600);
        clock.advance(Duration.ofMinutes(10));
        assertThat(limiter.allowed("ip")).isTrue();
    }

    @Test
    void eachPersonHasADailyAllowanceAndTheSiteHasACeiling() {
        UsageLimits limits = new UsageLimits(new DailyQuota(Clock.systemUTC()), 2, 3, 30, 0);
        limits.acquireAnalysis("anna", false);
        limits.acquireAnalysis("anna", false);
        assertThatThrownBy(() -> limits.acquireAnalysis("anna", false)).isInstanceOf(LimitReached.class)
                .hasMessageContaining("your 2 report analyses for today");
        limits.acquireAnalysis("ben", false);
        assertThatThrownBy(() -> limits.acquireAnalysis("cleo", false)).isInstanceOf(LimitReached.class)
                .hasMessageContaining("for everyone");
        // cleo's own allowance was given back when the site's ran out
        assertThat(limits.remaining("cleo", false).analysesLeft()).isZero();
        assertThat(limits.remaining("cleo", false).analysesPerDay()).isEqualTo(2);
        limits.releaseAnalysis("ben", false);
        assertThat(limits.remaining("ben", false).analysesLeft()).isEqualTo(1);
        // the admin is never limited
        for (int i = 0; i < 10; i++) {
            limits.acquireAnalysis("admin", true);
        }
        assertThat(limits.remaining("admin", true).analysesLeft()).isEqualTo(-1);
        assertThat(limits.remaining("anna", false).questionsLeft()).isEqualTo(30);
    }

    @Test
    void signupRulesExplainWhatToFix() {
        assertThat(SignupRules.problem("Shreya", "my-portfolio-2026")).isNull();
        assertThat(SignupRules.normalize("  Shreya ")).isEqualTo("shreya");
        assertThat(SignupRules.problem("ab", "my-portfolio-2026")).contains("3–30 characters");
        assertThat(SignupRules.problem("admin2", "my-portfolio-2026")).contains("reserved");
        assertThat(SignupRules.problem("shreya", "short")).contains("at least 10");
        assertThat(SignupRules.problem("shreya", "shreya2026!")).contains("too easy");
        assertThat(SignupRules.problem("shreya", "password123")).contains("too easy");
        assertThat(SignupRules.problem("shreya", "aaaaaaaaaaaa")).contains("too easy");
    }

    @Test
    void publicModeRefusesDevelopmentSecrets() {
        assertThat(PublicModeGuard.problems(false, "dev-only-secret", "admin-pass", "hrrag")).isEmpty();
        assertThat(PublicModeGuard.problems(true, "dev-only-secret-change-me-1234567890abcdef1234567890abcdef", "admin-pass", "hrrag"))
                .hasSize(3);
        assertThat(PublicModeGuard.problems(true, "x".repeat(48), "a-long-admin-password", "a-long-db-password")).isEmpty();
    }

    @Test
    void forwardedAddressIsOnlyTrustedWhenConfigured() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.18.0.5");
        request.addHeader("X-Forwarded-For", "203.0.113.9, 172.18.0.4");
        assertThat(ClientAddress.of(request, false)).isEqualTo("172.18.0.5");
        assertThat(ClientAddress.of(request, true)).isEqualTo("203.0.113.9");
    }
}
