package ru.anyforms.service.impl;

import org.junit.jupiter.api.Test;
import ru.anyforms.dto.cdek.CdekDeliveryEta;
import ru.anyforms.dto.cdek.CdekLocation;
import ru.anyforms.dto.cdek.CdekOrderInfo;
import ru.anyforms.dto.cdek.CdekPackage;
import ru.anyforms.dto.cdek.CdekTariffQuote;
import ru.anyforms.exception.CdekCalculationException;
import ru.anyforms.integration.CdekCalculatorGateway;
import ru.anyforms.integration.CdekTrackingGateway;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CdekDeliveryEtaResolverImplTest {

    private final CdekTrackingGateway tracking = mock(CdekTrackingGateway.class);
    private final CdekCalculatorGateway calculator = mock(CdekCalculatorGateway.class);
    private final CdekDeliveryEtaResolverImpl resolver = new CdekDeliveryEtaResolverImpl(tracking, calculator);

    private static final CdekLocation FROM = new CdekLocation("RU", "190000", "Санкт-Петербург", null);
    private static final CdekLocation TO = new CdekLocation("RU", "101000", "Москва", null);
    private static final List<CdekPackage> PACKAGES = List.of(new CdekPackage(300, 15, 15, 15));

    @Test
    void plannedDateWinsOverCalculator() {
        when(tracking.getOrderInfo("111")).thenReturn(new CdekOrderInfo(LocalDate.now().plusDays(2), 136, FROM, TO, PACKAGES));

        CdekDeliveryEta eta = resolver.resolve("111");

        assertEquals("2 дня", eta.daysText());
        verifyNoInteractions(calculator);
    }

    @Test
    void fallsBackToCalculatorRange() {
        when(tracking.getOrderInfo("111")).thenReturn(new CdekOrderInfo(null, 136, FROM, TO, PACKAGES));
        when(calculator.calculate(136, FROM, TO, PACKAGES)).thenReturn(new CdekTariffQuote(BigDecimal.TEN, 2, 3));

        assertEquals("2-3 дня", resolver.resolve("111").daysText());
    }

    @Test
    void noDateAndNoDataGivesNull() {
        when(tracking.getOrderInfo("111")).thenReturn(new CdekOrderInfo(null, null, null, null, List.of()));

        assertNull(resolver.resolve("111"));
        verifyNoInteractions(calculator);
    }

    @Test
    void calculatorFailureGivesNull() {
        when(tracking.getOrderInfo("111")).thenReturn(new CdekOrderInfo(null, 136, FROM, TO, PACKAGES));
        when(calculator.calculate(anyInt(), any(), any(), any())).thenThrow(new CdekCalculationException("нет тарифа"));

        assertNull(resolver.resolve("111"));
    }

    @Test
    void gatewayFailureOrBlankTrackerGivesNull() {
        when(tracking.getOrderInfo("111")).thenThrow(new RuntimeException("cdek down"));

        assertNull(resolver.resolve("111"));
        assertNull(resolver.resolve(" "));
        assertNull(resolver.resolve(null));
    }
}
