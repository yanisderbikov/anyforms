package ru.anyforms.service.salesbot;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class TimeWindowTest {

    @Test
    void parsesAndChecksBounds() {
        TimeWindow w = TimeWindow.parse("10:00-17:00");
        assertTrue(w.contains(LocalTime.of(10, 0)));
        assertTrue(w.contains(LocalTime.of(16, 59)));
        assertFalse(w.contains(LocalTime.of(17, 0)));
        assertFalse(w.contains(LocalTime.of(9, 59)));
        assertEquals("10:00-17:00", w.toString());
    }

    @Test
    void rejectsBrokenInput() {
        assertThrows(IllegalArgumentException.class, () -> TimeWindow.parse("17:00-10:00"));
        assertThrows(IllegalArgumentException.class, () -> TimeWindow.parse("10-17"));
        assertThrows(IllegalArgumentException.class, () -> TimeWindow.parse(null));
    }
}
