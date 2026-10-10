package ru.anyforms.dto.cdek;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CdekDeliveryEtaTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 21);

    @Test
    void plannedDateGivesExactDays() {
        CdekDeliveryEta eta = CdekDeliveryEta.ofPlannedDate(LocalDate.of(2026, 9, 24), TODAY);

        assertEquals(3, eta.daysMin());
        assertEquals(3, eta.daysMax());
        assertEquals("3 дня", eta.daysText());
        assertEquals("3 дня (до 24.09.2026)", eta.describe());
    }

    @Test
    void plannedDateTodayIsToday() {
        CdekDeliveryEta eta = CdekDeliveryEta.ofPlannedDate(TODAY, TODAY);

        assertEquals("сегодня", eta.daysText());
        assertEquals("сегодня (21.09.2026)", eta.describe());
        assertEquals("сегодня", CdekDeliveryEta.ofPeriod(0, 0).daysText());
    }

    @Test
    void pastPlannedDateGivesNoEta() {
        assertNull(CdekDeliveryEta.ofPlannedDate(LocalDate.of(2026, 9, 20), TODAY));
    }

    @Test
    void periodGivesRange() {
        CdekDeliveryEta eta = CdekDeliveryEta.ofPeriod(2, 3);

        assertEquals("2-3 дня", eta.daysText());
        assertEquals("2-3 дня", eta.describe());
        assertEquals("5-7 дней", CdekDeliveryEta.ofPeriod(5, 7).daysText());
        assertEquals("1 день", CdekDeliveryEta.ofPeriod(1, 1).daysText());
    }

    @Test
    void periodToleratesMissingOrSwappedBounds() {
        assertEquals("4 дня", CdekDeliveryEta.ofPeriod(null, 4).daysText());
        assertEquals("4 дня", CdekDeliveryEta.ofPeriod(4, null).daysText());
        assertEquals("2-3 дня", CdekDeliveryEta.ofPeriod(3, 2).daysText());
        assertNull(CdekDeliveryEta.ofPeriod(null, null));
    }

    @Test
    void nullDateGivesNoEta() {
        assertNull(CdekDeliveryEta.ofPlannedDate(null, TODAY));
    }

    @Test
    void russianPlurals() {
        assertEquals("день", CdekDeliveryEta.daysWord(1));
        assertEquals("дня", CdekDeliveryEta.daysWord(2));
        assertEquals("дней", CdekDeliveryEta.daysWord(5));
        assertEquals("дней", CdekDeliveryEta.daysWord(11));
        assertEquals("день", CdekDeliveryEta.daysWord(21));
    }
}
