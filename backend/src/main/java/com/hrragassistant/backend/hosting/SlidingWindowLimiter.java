package com.hrragassistant.backend.hosting;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "At most N events per key in the last window" (e.g. 10 failed sign-ins per
 * IP address per 15 minutes). Keys with no recent events are dropped, and the
 * map is bounded so a flood of fake addresses can't exhaust memory.
 */
public final class SlidingWindowLimiter {

    private static final int MAX_KEYS = 10_000;

    private final int maxEvents;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Long>> events = new ConcurrentHashMap<>();

    public SlidingWindowLimiter(int maxEvents, Duration window, Clock clock) {
        this.maxEvents = maxEvents;
        this.window = window;
        this.clock = clock;
    }

    /** True when the key is still under its limit (does not record anything). */
    public boolean allowed(String key) {
        return maxEvents <= 0 || count(key) < maxEvents;
    }

    public void record(String key) {
        if (maxEvents <= 0) {
            return;
        }
        if (events.size() >= MAX_KEYS) {
            prune();
            if (events.size() >= MAX_KEYS) {
                events.clear();
            }
        }
        Deque<Long> q = events.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            q.addLast(clock.millis());
        }
    }

    /** Records and reports whether the event was within the limit. */
    public boolean tryAcquire(String key) {
        synchronized (this) {
            if (!allowed(key)) {
                return false;
            }
            record(key);
            return true;
        }
    }

    public void reset(String key) {
        events.remove(key);
    }

    /** Seconds until the oldest counted event leaves the window (0 when not limited). */
    public long retryAfterSeconds(String key) {
        Deque<Long> q = events.get(key);
        if (q == null || allowed(key)) {
            return 0;
        }
        synchronized (q) {
            Long oldest = q.peekFirst();
            if (oldest == null) {
                return 0;
            }
            long ms = oldest + window.toMillis() - clock.millis();
            return Math.max(1, (ms + 999) / 1000);
        }
    }

    private int count(String key) {
        Deque<Long> q = events.get(key);
        if (q == null) {
            return 0;
        }
        synchronized (q) {
            long cutoff = clock.millis() - window.toMillis();
            while (!q.isEmpty() && q.peekFirst() <= cutoff) {
                q.pollFirst();
            }
            if (q.isEmpty()) {
                events.remove(key, q);
            }
            return q.size();
        }
    }

    private void prune() {
        for (String key : events.keySet()) {
            count(key);
        }
    }
}
