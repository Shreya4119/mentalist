package com.hrragassistant.backend.hosting;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Bot protection for sign-up without a third-party CAPTCHA (no tracking, no
 * extra cookies). Together with the per-address limit it stops the cheap,
 * scripted account creation that would burn the AI budget:
 *
 * - The form carries a signed, timestamped token fetched when the sign-up form
 *   opens; a POST without one, with a forged one, or sent within a couple of
 *   seconds of opening the form (faster than a person can type) is refused.
 * - A hidden "website" field that people never see or fill: bots that fill
 *   every field give themselves away.
 *
 * A determined attacker can still script a browser; this raises the cost of
 * abuse, the daily limits cap what any account can spend.
 */
@Component
public class SignupGuard {

    static final Duration MIN_AGE = Duration.ofSeconds(3);
    static final Duration MAX_AGE = Duration.ofHours(2);
    static final String REFUSED = "Sign-up couldn't be completed. Please reload the page and try again.";
    static final String EXPIRED = "This sign-up form has expired. Please try again.";
    static final String TOO_FAST = "That was quicker than a person can fill in the form. Please wait a moment and try again.";

    private final byte[] key;
    private final Clock clock;

    @Autowired
    public SignupGuard(@Value("${app.jwt.secret}") String secret) {
        this(secret, Clock.systemUTC());
    }

    SignupGuard(String secret, Clock clock) {
        // Derived from the JWT secret, so no extra setting; a different purpose label keeps the keys apart.
        this.key = hmac(secret.getBytes(StandardCharsets.UTF_8), "mentalist-signup-form");
        this.clock = clock;
    }

    /** A token for a freshly opened sign-up form: "issuedAtMillis.signature". */
    public String issue() {
        String ts = Long.toString(clock.millis());
        return ts + "." + sign(ts);
    }

    /** Null when the request looks like a person; otherwise the message to show. */
    public String problem(String token, String honeypot) {
        if (honeypot != null && !honeypot.isBlank()) {
            return REFUSED;
        }
        if (token == null) {
            return REFUSED;
        }
        int dot = token.indexOf('.');
        if (dot <= 0) {
            return REFUSED;
        }
        String ts = token.substring(0, dot);
        byte[] given = token.substring(dot + 1).getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(given, sign(ts).getBytes(StandardCharsets.US_ASCII))) {
            return REFUSED;
        }
        long issued;
        try {
            issued = Long.parseLong(ts);
        } catch (NumberFormatException e) {
            return REFUSED;
        }
        long age = clock.millis() - issued;
        if (age < MIN_AGE.toMillis()) {
            return age < -60_000 ? REFUSED : TOO_FAST;
        }
        return age > MAX_AGE.toMillis() ? EXPIRED : null;
    }

    private String sign(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(key, value));
    }

    private static byte[] hmac(byte[] key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }
}
