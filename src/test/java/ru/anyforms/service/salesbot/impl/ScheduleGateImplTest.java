package ru.anyforms.service.salesbot.impl;

import org.junit.jupiter.api.Test;
import ru.anyforms.model.salesbot.ScheduleSlot;
import ru.anyforms.repository.ScheduleSlotRepository;
import ru.anyforms.service.salesbot.JitterProvider;
import ru.anyforms.service.salesbot.ScheduleGate;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Тесты гейта расписания (на {@link Instant}): попадание в слот, учёт джиттера,
 * «один запуск в сутки».
 */
class ScheduleGateImplTest {

    private final ScheduleSlotRepository repository = mock(ScheduleSlotRepository.class);
    private final JitterProvider jitter = mock(JitterProvider.class);
    private final ScheduleGate gate = new ScheduleGateImpl(repository, jitter);

    /** Базовое время слота: значима только UTC-составляющая суток (дата — плейсхолдер 1970-01-01). */
    private static final Instant SLOT_0738 = Instant.parse("1970-01-01T07:38:00Z");

    private static ScheduleSlot slot(long id, int weekday, Instant timeUtc) {
        ScheduleSlot s = new ScheduleSlot();
        s.setId(id);
        s.setWeekday(weekday);
        s.setTimeUtc(timeUtc);
        s.setEnabled(true);
        return s;
    }

    private static int weekdayOf(Instant now) {
        return now.atOffset(ZoneOffset.UTC).getDayOfWeek().getValue();
    }

    @Test
    void firesOncePerDay_atSlotMinute_withZeroJitter() {
        Instant now = Instant.parse("2026-06-15T07:38:30Z");
        Instant day = now.truncatedTo(ChronoUnit.DAYS);
        int weekday = weekdayOf(now);

        when(repository.findByWeekdayAndEnabledIsTrue(weekday))
                .thenReturn(List.of(slot(1L, weekday, SLOT_0738)));
        when(jitter.jitterFor(1L, day)).thenReturn(Duration.ZERO);

        assertTrue(gate.tryClaimDueSlot(now), "должен сработать в минуту слота");
        assertFalse(gate.tryClaimDueSlot(now.plusSeconds(20)), "повторно в тот же день — нет");
    }

    @Test
    void firesAfterSlotMinute_catchUp() {
        // На минуту позже базового 07:38 — при catch-up слот всё равно должен сработать
        // (нужная минута могла быть пропущена из-за рестарта/редкого тика).
        Instant now = Instant.parse("2026-06-15T07:39:00Z");
        Instant day = now.truncatedTo(ChronoUnit.DAYS);
        int weekday = weekdayOf(now);

        when(repository.findByWeekdayAndEnabledIsTrue(weekday))
                .thenReturn(List.of(slot(1L, weekday, SLOT_0738)));
        when(jitter.jitterFor(1L, day)).thenReturn(Duration.ZERO);

        assertTrue(gate.tryClaimDueSlot(now), "catch-up: после эффективного времени — срабатывает");
        assertFalse(gate.tryClaimDueSlot(now.plusSeconds(90)), "повторно в тот же день — нет");
    }

    @Test
    void doesNotFire_beforeEffectiveMinute() {
        Instant now = Instant.parse("2026-06-15T07:37:00Z"); // на минуту РАНЬШЕ базового 07:38
        Instant day = now.truncatedTo(ChronoUnit.DAYS);
        int weekday = weekdayOf(now);

        when(repository.findByWeekdayAndEnabledIsTrue(weekday))
                .thenReturn(List.of(slot(1L, weekday, SLOT_0738)));
        when(jitter.jitterFor(1L, day)).thenReturn(Duration.ZERO);

        assertFalse(gate.tryClaimDueSlot(now));
    }

    @Test
    void jitterShiftsEffectiveFireMinute() {
        Instant base = Instant.parse("2026-06-15T07:38:00Z");
        Instant day = base.truncatedTo(ChronoUnit.DAYS);
        int weekday = weekdayOf(base);

        when(repository.findByWeekdayAndEnabledIsTrue(weekday))
                .thenReturn(List.of(slot(1L, weekday, SLOT_0738)));
        when(jitter.jitterFor(1L, day)).thenReturn(Duration.ofMinutes(2)); // эффективное 07:40

        assertFalse(gate.tryClaimDueSlot(base), "в 07:38 ещё рано — джиттер сдвинул на 07:40");
        assertTrue(gate.tryClaimDueSlot(base.plus(Duration.ofMinutes(2))), "в 07:40 — пора");
    }

    @Test
    void doesNotFire_whenNoSlotsForWeekday() {
        Instant now = Instant.parse("2026-06-15T07:38:00Z");
        when(repository.findByWeekdayAndEnabledIsTrue(anyInt())).thenReturn(List.of());

        assertFalse(gate.tryClaimDueSlot(now));
        verify(jitter, never()).jitterFor(anyLong(), any());
    }
}
