package ru.anyforms.service.promo.impl;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimRateLimiterTest {

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private final Clock clock = new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(now.get());
        }
    };
    private final ClaimRateLimiter limiter = new ClaimRateLimiter(clock);

    @Test
    void blocksAfterLimitAndReleasesAfterWindow() {
        for (int i = 0; i < ClaimRateLimiter.CLAIM_ATTEMPTS; i++) {
            assertTrue(limiter.tryAcquire("ip", ClaimRateLimiter.CLAIM_ATTEMPTS));
        }
        assertFalse(limiter.tryAcquire("ip", ClaimRateLimiter.CLAIM_ATTEMPTS));
        assertTrue(limiter.tryAcquire("other-ip", ClaimRateLimiter.CLAIM_ATTEMPTS));

        now.addAndGet(ClaimRateLimiter.WINDOW_MS);

        assertTrue(limiter.tryAcquire("ip", ClaimRateLimiter.CLAIM_ATTEMPTS));
    }
}
