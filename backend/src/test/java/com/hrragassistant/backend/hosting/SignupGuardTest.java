package com.hrragassistant.backend.hosting;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class SignupGuardTest {

    private static final String SECRET = "test-secret-0123456789abcdef0123456789abcdef";

    @Test
    void aPersonFillingTheFormPasses() {
        Instant opened = Instant.parse("2026-11-23T10:00:00Z");
        String token = new SignupGuard(SECRET, Clock.fixed(opened, ZoneOffset.UTC)).issue();
        SignupGuard later = new SignupGuard(SECRET, Clock.fixed(opened.plusSeconds(25), ZoneOffset.UTC));
        assertThat(later.problem(token, "")).isNull();
        assertThat(later.problem(token, null)).isNull();
    }

    @Test
    void botsAreRefused() {
        Instant opened = Instant.parse("2026-11-23T10:00:00Z");
        String token = new SignupGuard(SECRET, Clock.fixed(opened, ZoneOffset.UTC)).issue();
        SignupGuard at = new SignupGuard(SECRET, Clock.fixed(opened.plusSeconds(30), ZoneOffset.UTC));
        assertThat(at.problem(null, null)).isEqualTo(SignupGuard.REFUSED);                     // no form token
        assertThat(at.problem("garbage", null)).isEqualTo(SignupGuard.REFUSED);
        assertThat(at.problem(token, "https://spam.example")).isEqualTo(SignupGuard.REFUSED);  // hidden field filled
        String forged = (opened.toEpochMilli() - 60_000) + token.substring(token.indexOf('.'));
        assertThat(at.problem(forged, null)).isEqualTo(SignupGuard.REFUSED);                   // timestamp changed
        String otherKey = new SignupGuard("another-secret-0123456789abcdef0123456789", Clock.fixed(opened, ZoneOffset.UTC)).issue();
        assertThat(at.problem(otherKey, null)).isEqualTo(SignupGuard.REFUSED);
        SignupGuard instantly = new SignupGuard(SECRET, Clock.fixed(opened.plusMillis(800), ZoneOffset.UTC));
        assertThat(instantly.problem(token, null)).isEqualTo(SignupGuard.TOO_FAST);
        SignupGuard muchLater = new SignupGuard(SECRET, Clock.fixed(opened.plus(Duration.ofHours(3)), ZoneOffset.UTC));
        assertThat(muchLater.problem(token, null)).isEqualTo(SignupGuard.EXPIRED);
    }
}
