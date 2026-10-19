package com.hrragassistant.backend.reports.ai;

import java.util.Set;

/**
 * Decides whether a failed provider call is worth repeating: rate limits,
 * overloaded or briefly unavailable servers, network timeouts and the
 * occasional malformed JSON answer usually succeed on a second try; a bad
 * key, a missing model or an unreadable file never do.
 */
public final class RetryPolicy {

    public static final int MAX_ATTEMPTS = 3;

    private static final Set<Integer> TRANSIENT_STATUS = Set.of(408, 425, 429, 500, 502, 503, 504, 529);

    private RetryPolicy() {
    }

    /** Seconds to wait before attempt {@code nextAttempt} (2 or 3). */
    public static long backoffSeconds(int nextAttempt) {
        return nextAttempt <= 2 ? 15 : 45;
    }

    public static boolean isTransientStatus(int status) {
        return TRANSIENT_STATUS.contains(status);
    }

    /** Walks the cause chain; {@code statusOf} extracts an HTTP status from provider exceptions (or -1). */
    public static boolean isTransient(Throwable t, java.util.function.ToIntFunction<Throwable> statusOf) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            int status = statusOf.applyAsInt(c);
            if (status > 0) {
                return isTransientStatus(status);
            }
            if (isTransientType(c)) {
                return true;
            }
            if (c.getCause() == c) {
                break;
            }
        }
        return false;
    }

    private static boolean isTransientType(Throwable c) {
        for (Class<?> k = c.getClass(); k != null; k = k.getSuperclass()) {
            String n = k.getName();
            if (n.equals("java.net.http.HttpTimeoutException")
                    || n.equals("java.net.SocketTimeoutException")
                    || n.equals("java.net.http.HttpConnectTimeoutException")
                    || n.equals("org.springframework.web.client.ResourceAccessException")
                    || n.equals("com.fasterxml.jackson.core.JsonProcessingException")) {
                return true;
            }
        }
        String msg = c.getMessage() == null ? "" : c.getMessage();
        return msg.contains("returned an empty response") || msg.contains("returned no content")
                || msg.contains("did not return the structured result");
    }
}
