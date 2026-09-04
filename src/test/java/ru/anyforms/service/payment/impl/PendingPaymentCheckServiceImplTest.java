package ru.anyforms.service.payment.impl;

import org.junit.jupiter.api.Test;
import ru.anyforms.dto.payment.tinkoff.TinkoffGetStateResponse;
import ru.anyforms.model.payment.PaymentProduct;
import ru.anyforms.model.payment.PaymentProvider;
import ru.anyforms.model.payment.PaymentTransaction;
import ru.anyforms.model.payment.PaymentTransactionStatus;
import ru.anyforms.repository.GetterTransaction;
import ru.anyforms.service.payment.PaymentConfirmService;
import ru.anyforms.service.payment.PaymentStatusConverter;
import ru.anyforms.service.payment.TinkoffService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PendingPaymentCheckServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-09-03T12:00:00Z");
    private static final Duration TTL = TinkoffPaymentSupport.CART_LINK_TTL;

    private final GetterTransaction getterTransaction = mock(GetterTransaction.class);
    private final TinkoffService tinkoffService = mock(TinkoffService.class);
    private final PaymentConfirmService paymentConfirmService = mock(PaymentConfirmService.class);
    private final PendingPaymentCheckServiceImpl service = new PendingPaymentCheckServiceImpl(
            getterTransaction, tinkoffService, new PaymentStatusConverter(), paymentConfirmService,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private static PaymentTransaction pending(Instant createdAt) {
        return PaymentTransaction.builder()
                .id(UUID.randomUUID())
                .provider(PaymentProvider.TINKOFF)
                .productCode(PaymentProduct.CODE_MARKETPLACE_CART)
                .status(PaymentTransactionStatus.PENDING)
                .externalPaymentId("pay-" + UUID.randomUUID())
                .createdAt(createdAt)
                .build();
    }

    private void expiredCandidates(PaymentTransaction... transactions) {
        when(getterTransaction.getByProviderStatusAndProductCodeCreatedBetween(
                PaymentProvider.TINKOFF, PaymentTransactionStatus.PENDING, PaymentProduct.CODE_MARKETPLACE_CART,
                NOW.minus(Duration.ofDays(1)), NOW.minus(TTL).minus(PendingPaymentCheckServiceImpl.BANK_GRACE)))
                .thenReturn(List.of(transactions));
        when(paymentConfirmService.applyStatus(anyString(), any())).thenReturn(true);
    }

    private void bankSays(PaymentTransaction transaction, String status) {
        TinkoffGetStateResponse response = new TinkoffGetStateResponse();
        response.setSuccess(true);
        response.setStatus(status);
        response.setPaymentId(transaction.getExternalPaymentId());
        when(tinkoffService.getState(transaction.getExternalPaymentId())).thenReturn(response);
    }

    @Test
    void expiredLinkIsCanceledThroughConfirmChain() {
        PaymentTransaction transaction = pending(NOW.minus(TTL).minus(Duration.ofMinutes(10)));
        expiredCandidates(transaction);
        bankSays(transaction, "DEADLINE_EXPIRED");

        assertEquals(1, service.check());

        verify(paymentConfirmService).applyStatus(transaction.getExternalPaymentId(), PaymentTransactionStatus.CANCELED);
    }

    @Test
    void paidWithoutWebhookIsFulfilled() {
        PaymentTransaction transaction = pending(NOW.minus(TTL).minus(Duration.ofMinutes(10)));
        expiredCandidates(transaction);
        bankSays(transaction, "CONFIRMED");

        assertEquals(1, service.check());

        verify(paymentConfirmService).applyStatus(transaction.getExternalPaymentId(), PaymentTransactionStatus.SUCCEEDED);
    }

    @Test
    void waitsWhileBankStillHoldsIntermediateStatus() {
        PaymentTransaction transaction = pending(NOW.minus(TTL).minus(Duration.ofMinutes(10)));
        expiredCandidates(transaction);
        bankSays(transaction, "FORM_SHOWED");

        assertEquals(0, service.check());

        verify(paymentConfirmService, never()).applyStatus(anyString(), any());
    }

    @Test
    void forcesCancelWhenLinkExpiredLongAgoButBankNeverMovedIt() {
        PaymentTransaction transaction = pending(NOW.minus(TTL).minus(Duration.ofMinutes(31)));
        expiredCandidates(transaction);
        bankSays(transaction, "NEW");

        assertEquals(1, service.check());

        verify(paymentConfirmService).applyStatus(transaction.getExternalPaymentId(), PaymentTransactionStatus.CANCELED);
    }

    @Test
    void unknownBankStatusIsSkipped() {
        PaymentTransaction transaction = pending(NOW.minus(TTL).minus(Duration.ofMinutes(10)));
        expiredCandidates(transaction);
        bankSays(transaction, "SOMETHING_NEW");

        assertEquals(0, service.check());

        verify(paymentConfirmService, never()).applyStatus(anyString(), any());
    }

    @Test
    void bankErrorOnOneTransactionDoesNotStopOthers() {
        PaymentTransaction broken = pending(NOW.minus(TTL).minus(Duration.ofMinutes(10)));
        PaymentTransaction expired = pending(NOW.minus(TTL).minus(Duration.ofMinutes(12)));
        expiredCandidates(broken, expired);
        when(tinkoffService.getState(broken.getExternalPaymentId())).thenThrow(new RuntimeException("Т-Касса недоступна"));
        bankSays(expired, "REJECTED");

        assertEquals(1, service.check());

        verify(paymentConfirmService).applyStatus(expired.getExternalPaymentId(), PaymentTransactionStatus.CANCELED);
    }
}
