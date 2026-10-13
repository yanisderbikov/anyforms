package ru.anyforms.service.impl;

import org.junit.jupiter.api.Test;
import ru.anyforms.event.CustomProductAmoNoteEvent;
import ru.anyforms.integration.AmoCrmGateway;
import ru.anyforms.util.retry.Retrier;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomProductAmoNoteListenerTest {

    // 06:34 UTC = 9:34 МСК
    private static final Instant AT = Instant.parse("2026-10-13T06:34:00Z");
    private final CustomProductAmoNoteEvent event =
            new CustomProductAmoNoteEvent(1L, AT, "комментарий производства:\n\nОтлили 20 шт");

    @Test
    void noteIsMoscowTimeAndText() {
        assertEquals("Время МСК 9:34 — комментарий производства:\n\nОтлили 20 шт",
                CustomProductAmoNoteListener.noteText(event));
    }

    @Test
    void sendsNoteThroughRetrier() {
        AmoCrmGateway amo = mock(AmoCrmGateway.class);
        when(amo.addNoteToLead(eq(1L), anyString())).thenReturn(false, true);
        Retrier noPauseRetrier = (name, max, pause, call) -> call.getAsBoolean() || call.getAsBoolean();

        new CustomProductAmoNoteListener(amo, noPauseRetrier).onNote(event);

        verify(amo, times(2)).addNoteToLead(1L, CustomProductAmoNoteListener.noteText(event));
    }
}
