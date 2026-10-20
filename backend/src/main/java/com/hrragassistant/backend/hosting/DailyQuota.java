package com.hrragassistant.backend.hosting;

import java.time.Clock;
import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Counters that reset every calendar day (in the clock's zone). Used to cap
 * how many AI calls the public server makes per day, so a busy day or a bot
 * can't run up the AI bill. In memory on purpose: the server runs as one
 * instance, and a restart resetting the counters is harmless.
 */
public final class DailyQuota {

    private final Clock clock;
    private final ConcurrentHashMap<String, AtomicInteger> counts = new ConcurrentHashMap<>();
    private LocalDate day;

    public DailyQuota(Clock clock) {
        this.clock = clock;
    }

    /** Takes one unit if fewer than {@code limit} were used today. A limit of 0 or less means unlimited. */
    public boolean tryAcquire(String key, int limit) {
        if (limit <= 0) {
            return true;
        }
        rollover();
        AtomicInteger counter = counts.computeIfAbsent(key, k -> new AtomicInteger());
        while (true) {
            int used = counter.get();
            if (used >= limit) {
                return false;
            }
            if (counter.compareAndSet(used, used + 1)) {
                return true;
            }
        }
    }

    /** Gives a unit back, e.g. when the work it was taken for didn't happen. */
    public void release(String key) {
        rollover();
        AtomicInteger counter = counts.get(key);
        if (counter != null) {
            counter.updateAndGet(v -> Math.max(0, v - 1));
        }
    }

    public int used(String key) {
        rollover();
        AtomicInteger counter = counts.get(key);
        return counter == null ? 0 : counter.get();
    }

    /** Units left today, or -1 when unlimited. */
    public int remaining(String key, int limit) {
        return limit <= 0 ? -1 : Math.max(0, limit - used(key));
    }

    private synchronized void rollover() {
        LocalDate today = LocalDate.now(clock);
        if (!today.equals(day)) {
            counts.clear();
            day = today;
        }
    }
}
