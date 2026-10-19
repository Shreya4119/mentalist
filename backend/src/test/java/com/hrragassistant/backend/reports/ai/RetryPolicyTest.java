package com.hrragassistant.backend.reports.ai;

import java.net.SocketTimeoutException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RetryPolicyTest {

    @Test
    void retriesRateLimitsOverloadsAndTimeoutsOnly() {
        assertThat(RetryPolicy.isTransientStatus(429)).isTrue();
        assertThat(RetryPolicy.isTransientStatus(503)).isTrue();
        assertThat(RetryPolicy.isTransientStatus(529)).isTrue();
        assertThat(RetryPolicy.isTransientStatus(400)).isFalse();
        assertThat(RetryPolicy.isTransientStatus(401)).isFalse();
        assertThat(RetryPolicy.isTransientStatus(404)).isFalse();
    }

    @Test
    void looksThroughWrappedCauses() {
        Exception timeout = new IllegalStateException("Gemini: timed out", new SocketTimeoutException("read timed out"));
        assertThat(RetryPolicy.isTransient(timeout, t -> -1)).isTrue();
        assertThat(RetryPolicy.isTransient(new IllegalStateException("Gemini returned no content"), t -> -1)).isTrue();
        assertThat(RetryPolicy.isTransient(new IllegalStateException("The API key was rejected"), t -> -1)).isFalse();
        assertThat(RetryPolicy.isTransient(new RuntimeException("x"), t -> 429)).isTrue();
        assertThat(RetryPolicy.isTransient(new RuntimeException("x"), t -> 400)).isFalse();
    }
}
