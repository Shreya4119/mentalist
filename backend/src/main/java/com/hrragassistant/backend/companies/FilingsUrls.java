package com.hrragassistant.backend.companies;

import java.net.URI;
import java.util.Locale;

/**
 * Every address the app fetches or links to comes from the filings index's
 * answers. Those are outside data, so each one is resolved against the
 * configured service and must stay on that same host: otherwise a tampered
 * answer could make the server request internal addresses (cloud metadata,
 * Redis, the database) on an attacker's behalf (server-side request forgery).
 */
final class FilingsUrls {

    private FilingsUrls() {
    }

    /** The address resolved against the service, or null when it points anywhere else. */
    static URI onService(URI base, String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI u = base.resolve(url.trim().replace(" ", "%20"));
            if (u.getRawUserInfo() != null || u.getScheme() == null || u.getHost() == null) {
                return null;
            }
            boolean sameScheme = u.getScheme().equalsIgnoreCase(base.getScheme());
            boolean sameHost = u.getHost().toLowerCase(Locale.ROOT).equals(base.getHost().toLowerCase(Locale.ROOT));
            boolean samePort = port(u) == port(base);
            return sameScheme && sameHost && samePort ? u : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int port(URI u) {
        if (u.getPort() != -1) {
            return u.getPort();
        }
        return "https".equalsIgnoreCase(u.getScheme()) ? 443 : 80;
    }
}
