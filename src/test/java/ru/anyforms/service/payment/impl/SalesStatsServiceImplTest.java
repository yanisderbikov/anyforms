package ru.anyforms.service.payment.impl;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;
import ru.anyforms.dto.payment.ProductSalesDTO;
import ru.anyforms.model.payment.PaymentProduct;
import ru.anyforms.model.payment.PaymentTransactionStatus;
import ru.anyforms.repository.GetterTransaction;
import ru.anyforms.repository.ProductSalesRow;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Тесты статистики продаж: границы суток по МСК, фильтр по статусу и продуктам,
 * нулевые позиции и валидация дат.
 */
class SalesStatsServiceImplTest {

    private record Row(String code, long quantity, long amountKopecks) implements ProductSalesRow {

        @Override
        public String getProductCode() {
            return code;
        }

        @Override
        public long getQuantity() {
            return quantity;
        }

        @Override
        public long getAmountKopecks() {
            return amountKopecks;
        }
    }

    private final GetterTransaction getterTransaction = mock(GetterTransaction.class);
    private final SalesStatsServiceImpl service = new SalesStatsServiceImpl(getterTransaction);

    @Test
    void returnsAllThreeProductsWithZeroesForMissingOnes() {
        when(getterTransaction.getSalesByProductCodes(any(), any(), any(), any()))
                .thenReturn(List.of(
                        new Row(PaymentProduct.CODE_GUIDE, 10, 1_000_000L),
                        new Row(PaymentProduct.CODE_COURSE, 1, 300_000L)));

        List<ProductSalesDTO> sales = service.getTrainingSales(
                LocalDate.of(2026, 7, 28), LocalDate.of(2026, 7, 28));

        assertEquals(List.of(
                new ProductSalesDTO(PaymentProduct.CODE_GUIDE, 10, 1_000_000L),
                new ProductSalesDTO(PaymentProduct.CODE_COURSE, 1, 300_000L),
                new ProductSalesDTO(PaymentProduct.CODE_COURSE_PERSONAL, 0, 0L)), sales);
    }

    @Test
    void asksOnlyForSucceededTrainingSalesWithinMoscowDay() {
        when(getterTransaction.getSalesByProductCodes(any(), any(), any(), any())).thenReturn(List.of());

        service.getTrainingSales(LocalDate.of(2026, 7, 28), LocalDate.of(2026, 7, 28));

        ArgumentCaptor<Collection<String>> codes = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> to = ArgumentCaptor.forClass(Instant.class);
        verify(getterTransaction).getSalesByProductCodes(
                eq(PaymentTransactionStatus.SUCCEEDED), codes.capture(), from.capture(), to.capture());

        assertEquals(List.of(PaymentProduct.CODE_GUIDE, PaymentProduct.CODE_COURSE,
                PaymentProduct.CODE_COURSE_PERSONAL), List.copyOf(codes.getValue()));
        assertEquals(Instant.parse("2026-07-27T21:00:00Z"), from.getValue());
        assertEquals(Instant.parse("2026-07-28T21:00:00Z"), to.getValue());
    }

    @Test
    void multiDayRangeEndsAtMoscowMidnightAfterLastDay() {
        when(getterTransaction.getSalesByProductCodes(any(), any(), any(), any())).thenReturn(List.of());

        service.getTrainingSales(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 26));

        ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> to = ArgumentCaptor.forClass(Instant.class);
        verify(getterTransaction).getSalesByProductCodes(any(), any(), from.capture(), to.capture());

        assertEquals(Instant.parse("2026-07-19T21:00:00Z"), from.getValue());
        assertEquals(Instant.parse("2026-07-26T21:00:00Z"), to.getValue());
    }

    @Test
    void rejectsReversedAndMissingDates() {
        assertThrows(ResponseStatusException.class, () -> service.getTrainingSales(
                LocalDate.of(2026, 7, 28), LocalDate.of(2026, 7, 20)));
        assertThrows(ResponseStatusException.class, () -> service.getTrainingSales(
                null, LocalDate.of(2026, 7, 28)));
        assertThrows(ResponseStatusException.class, () -> service.getTrainingSales(
                LocalDate.of(2026, 7, 28), null));

        verifyNoInteractions(getterTransaction);
    }
}
