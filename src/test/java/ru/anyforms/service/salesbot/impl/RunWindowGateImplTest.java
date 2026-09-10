package ru.anyforms.service.salesbot.impl;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/** Гейт прогона: минимальный интервал между прогонами. */
class RunWindowGateImplTest {

    private final RunWindowGateImpl gate = new RunWindowGateImpl(30);

    @Test
    void firesThenWaitsForInterval() {
        Instant t = Instant.parse("2026-09-09T07:00:00Z");
        assertTrue(gate.tryClaim(t));
        assertFalse(gate.tryClaim(t.plusSeconds(10 * 60)));
        assertTrue(gate.tryClaim(t.plusSeconds(30 * 60)));
    }

    @Test
    void rejectsBrokenInterval() {
        assertThrows(IllegalArgumentException.class, () -> new RunWindowGateImpl(0));
    }
}
