package ru.anyforms.util.retry;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SleepingRetrierTest {

    private final Retrier retrier = new SleepingRetrier();

    @Test
    void stopsOnFirstSuccess() {
        AtomicInteger calls = new AtomicInteger();

        assertTrue(retrier.attempt("test", 3, Duration.ZERO, () -> calls.incrementAndGet() == 2));
        assertEquals(2, calls.get());
    }

    @Test
    void exceptionCountsAsFailedAttempt() {
        AtomicInteger calls = new AtomicInteger();

        boolean ok = retrier.attempt("test", 3, Duration.ZERO, () -> {
            calls.incrementAndGet();
            throw new IllegalStateException("boom");
        });

        assertFalse(ok);
        assertEquals(3, calls.get());
    }

    @Test
    void moreThanTenAttemptsIsRejectedBeforeAnyCall() {
        AtomicInteger calls = new AtomicInteger();

        assertThrows(IllegalArgumentException.class,
                () -> retrier.attempt("test", 11, Duration.ZERO, () -> calls.incrementAndGet() > 0));
        assertThrows(IllegalArgumentException.class,
                () -> retrier.attempt("test", 0, Duration.ZERO, () -> calls.incrementAndGet() > 0));
        assertEquals(0, calls.get());
    }

    @Test
    void tenAttemptsIsAllowed() {
        AtomicInteger calls = new AtomicInteger();

        assertFalse(retrier.attempt("test", 10, Duration.ZERO, () -> calls.incrementAndGet() < 0));
        assertEquals(10, calls.get());
    }
}
