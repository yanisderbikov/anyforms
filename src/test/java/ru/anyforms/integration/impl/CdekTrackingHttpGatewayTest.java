package ru.anyforms.integration.impl;

import org.junit.jupiter.api.Test;
import ru.anyforms.dto.cdek.CdekOrderInfo;
import ru.anyforms.dto.cdek.CdekPackage;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CdekTrackingHttpGatewayTest {

    @Test
    void parsesPlannedDateTariffLocationsAndPackages() {
        String json = "{\"entity\":{\"cdek_number\":\"1234567890\",\"tariff_code\":136,"
                + "\"planned_delivery_date\":\"2026-09-24\","
                + "\"from_location\":{\"code\":137,\"city\":\"Санкт-Петербург\",\"postal_code\":\"190000\",\"address\":\"Невский 1\",\"country_code\":\"RU\"},"
                + "\"to_location\":{\"code\":44,\"city\":\"Москва\",\"postal_code\":\"101000\",\"address\":\"Тверская 1\"},"
                + "\"packages\":[{\"number\":\"1\",\"weight\":300,\"length\":15,\"width\":15,\"height\":15},{\"weight\":0}],"
                + "\"statuses\":[]}}";

        CdekOrderInfo info = CdekTrackingHttpGateway.parseOrderInfo(json);

        assertEquals(LocalDate.of(2026, 9, 24), info.plannedDeliveryDate());
        assertEquals(136, info.tariffCode());
        assertEquals("190000", info.from().postalCode());
        assertEquals("Москва", info.to().city());
        assertEquals("RU", info.to().countryCode());
        assertEquals(1, info.packages().size());
        assertEquals(new CdekPackage(300, 15, 15, 15), info.packages().get(0));
        assertTrue(info.canCalculate());
    }

    @Test
    void parsesDateTimeVariant() {
        String json = "{\"entity\":{\"planned_delivery_date\":\"2026-09-24T00:00:00+0300\"}}";

        assertEquals(LocalDate.of(2026, 9, 24), CdekTrackingHttpGateway.parseOrderInfo(json).plannedDeliveryDate());
    }

    @Test
    void missingDateAndDataGivesNothingToCalculate() {
        CdekOrderInfo info = CdekTrackingHttpGateway.parseOrderInfo("{\"entity\":{\"statuses\":[],\"planned_delivery_date\":null}}");

        assertNull(info.plannedDeliveryDate());
        assertFalse(info.canCalculate());
        assertNull(CdekTrackingHttpGateway.parseOrderInfo("{\"entity\":{\"planned_delivery_date\":\"soon\"}}").plannedDeliveryDate());
    }

    @Test
    void brokenResponseGivesNull() {
        assertNull(CdekTrackingHttpGateway.parseOrderInfo(null));
        assertNull(CdekTrackingHttpGateway.parseOrderInfo("{\"requests\":[]}"));
    }
}
