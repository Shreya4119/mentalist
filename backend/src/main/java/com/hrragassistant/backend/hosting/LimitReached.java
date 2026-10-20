package com.hrragassistant.backend.hosting;

/** A daily AI cap or a rate limit was hit; answered with 429 and a plain-language message. */
public class LimitReached extends RuntimeException {

    private final long retryAfterSeconds;

    public LimitReached(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
