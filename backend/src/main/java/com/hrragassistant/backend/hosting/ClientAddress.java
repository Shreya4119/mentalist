package com.hrragassistant.backend.hosting;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The visitor's IP address, for rate limiting. Behind the public deployment's
 * proxies (Caddy, then nginx) the socket address is the proxy's, so the
 * address Caddy wrote into X-Forwarded-For is used instead. Only trust that
 * header when the backend can't be reached directly (TRUST_FORWARDED_FOR).
 */
public final class ClientAddress {

    private ClientAddress() {
    }

    public static String of(HttpServletRequest request, boolean trustForwardedFor) {
        if (trustForwardedFor) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String first = forwarded.split(",")[0].trim();
                if (!first.isEmpty() && first.length() <= 64) {
                    return first;
                }
            }
        }
        return request.getRemoteAddr();
    }
}
