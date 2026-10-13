package ru.anyforms.service.impl;

import org.junit.jupiter.api.Test;
import ru.anyforms.event.ProductionLogAddedEvent;
import ru.anyforms.integration.AmoCrmGateway;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductionLogAmoNoteListenerTest {

    // 06:34 UTC = 9:34 МСК
    private final ProductionLogAddedEvent event =
            new ProductionLogAddedEvent(1L, "Отлили 20 шт", Instant.parse("2026-10-13T06:34:00Z"));

    @Test
    void noteHasMoscowTimeAndText() {
        assertEquals("Время МСК 9:34 — комментарий производства:\n\nОтлили 20 шт",
                ProductionLogAmoNoteListener.noteText(event));
    }

    @Test
    void retriesUntilAmoAccepts() {
        AmoCrmGateway amo = mock(AmoCrmGateway.class);
        when(amo.addNoteToLead(eq(1L), anyString())).thenReturn(false, true);

        new ProductionLogAmoNoteListener(amo).onLogAdded(event);

        verify(amo, times(2)).addNoteToLead(eq(1L), anyString());
    }
}
