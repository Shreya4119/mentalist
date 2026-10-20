package com.hrragassistant.backend.auth;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final String ROLE_CLAIM = "role";

    private final SecretKey key;
    private final long expirationMillis;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMillis) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMillis = expirationMillis;
    }

    public String issueToken(String username, Role role) {
        return issueToken(username, role, expirationMillis);
    }

    /** A token with its own lifetime, e.g. a short public-demo guest session. */
    public String issueToken(String username, Role role, long ttlMillis) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + ttlMillis);

        return Jwts.builder()
                .subject(username)
                .claim(ROLE_CLAIM, role.name())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    /**
     * Returns the validated claims, or empty if the token is missing, expired, or tampered with.
     */
    public java.util.Optional<Claims> parseClaims(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            // A token minted before the role rename (HR_ADMIN/EMPLOYEE) is treated as
            // invalid -> 401, so the app simply asks the user to sign in again.
            roleFromClaims(claims);
            return java.util.Optional.of(claims);
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            return java.util.Optional.empty();
        }
    }

    public Role roleFromClaims(Claims claims) {
        return Role.valueOf(claims.get(ROLE_CLAIM, String.class));
    }
}
