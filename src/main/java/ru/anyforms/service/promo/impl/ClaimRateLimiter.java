package ru.anyforms.service.promo.impl;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
class ClaimRateLimiter {

    static final int CLAIM_ATTEMPTS = 10;
    static final int ISSUE_ATTEMPTS = 30;
    static final long WINDOW_MS = 10 * 60 * 1000L;
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final Map<String, Deque<Long>> attemptsByKey = new ConcurrentHashMap<>();
    private final Clock clock;

    ClaimRateLimiter() {
        this(Clock.systemUTC());
    }

    ClaimRateLimiter(Clock clock) {
        this.clock = clock;
    }

    boolean tryAcquire(String key, int maxAttempts) {
        if (key == null || key.isBlank()) {
            return true;
        }
        long now = clock.millis();
        if (attemptsByKey.size() > CLEANUP_THRESHOLD) {
            attemptsByKey.entrySet().removeIf(e -> isStale(e.getValue(), now));
        }
        Deque<Long> attempts = attemptsByKey.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (attempts) {
            while (!attempts.isEmpty() && now - attempts.peekFirst() >= WINDOW_MS) {
                attempts.pollFirst();
            }
            if (attempts.size() >= maxAttempts) {
                return false;
            }
            attempts.addLast(now);
            return true;
        }
    }

    private boolean isStale(Deque<Long> attempts, long now) {
        synchronized (attempts) {
            return attempts.isEmpty() || now - attempts.peekLast() >= WINDOW_MS;
        }
    }
}
