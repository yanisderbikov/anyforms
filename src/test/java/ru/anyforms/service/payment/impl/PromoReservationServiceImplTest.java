package ru.anyforms.service.payment.impl;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.anyforms.dto.payment.tinkoff.TinkoffCancelRequest;
import ru.anyforms.dto.payment.tinkoff.TinkoffCancelResponse;
import ru.anyforms.dto.payment.tinkoff.TinkoffGetStateResponse;
import ru.anyforms.model.payment.PaymentProvider;
import ru.anyforms.model.payment.PaymentTransaction;
import ru.anyforms.model.payment.PaymentTransactionStatus;
import ru.anyforms.model.payment.PromoCode;
import ru.anyforms.repository.GetterTransaction;
import ru.anyforms.service.payment.PaymentConfirmService;
import ru.anyforms.service.payment.PaymentStatusConverter;
import ru.anyforms.service.payment.TinkoffService;
import ru.anyforms.service.promo.PromoClient;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PromoReservationServiceImplTest {

    private static final String DEVICE = "6f1c7a52-8b1e-4c39-9d7e-0a2b3c4d5e6f";

    private final GetterTransaction getterTransaction = mock(GetterTransaction.class);
    private final TinkoffService tinkoffService = mock(TinkoffService.class);
    private final PaymentStatusConverter paymentStatusConverter = mock(PaymentStatusConverter.class);
    private final PaymentConfirmService paymentConfirmService = mock(PaymentConfirmService.class);
    private final PromoReservationServiceImpl service = new PromoReservationServiceImpl(getterTransaction,
            tinkoffService, paymentStatusConverter, paymentConfirmService);

    private final PromoCode limited = PromoCode.builder().code("ONE-AAAAA").maxUses(1).build();
    private final PromoClient client = PromoClient.of("buyer@example.com", "+79991234567", DEVICE);

    private PaymentTransaction pending(PaymentProvider provider) {
        PaymentTransaction transaction = PaymentTransaction.builder()
                .id(UUID.randomUUID())
                .provider(provider)
                .externalPaymentId("ext-" + provider)
                .status(PaymentTransactionStatus.PENDING)
                .promoCode("ONE-AAAAA")
                .build();
        when(getterTransaction.getPendingByPromoCodeAndCustomer(eq("ONE-AAAAA"), eq("buyer@example.com"),
                eq("9991234567"), eq(DEVICE), any())).thenReturn(List.of(transaction));
        return transaction;
    }

    private void bankSays(String status, PaymentTransactionStatus mapped) {
        TinkoffGetStateResponse state = new TinkoffGetStateResponse();
        state.setStatus(status);
        when(tinkoffService.getState("ext-TINKOFF")).thenReturn(state);
        when(paymentStatusConverter.fromTinkoff(status)).thenReturn(mapped);
    }

    @Test
    void unlimitedCodesAndAnonymousClientsAreIgnored() {
        service.releaseOwnReservations(PromoCode.builder().code("ANY-10").build(), client);
        service.releaseOwnReservations(limited, PromoClient.of(null, null, null));

        verifyNoInteractions(getterTransaction, tinkoffService, paymentConfirmService);
    }

    @Test
    void unpaidTinkoffLinkIsCancelledAndSuperseded() {
        pending(PaymentProvider.TINKOFF);
        bankSays("NEW", PaymentTransactionStatus.PENDING);
        when(tinkoffService.cancel(any())).thenReturn(new TinkoffCancelResponse());

        service.releaseOwnReservations(limited, client);

        ArgumentCaptor<TinkoffCancelRequest> cancel = ArgumentCaptor.forClass(TinkoffCancelRequest.class);
        verify(tinkoffService).cancel(cancel.capture());
        assertEquals("ext-TINKOFF", cancel.getValue().getPaymentId());
        verify(paymentConfirmService).supersede("ext-TINKOFF");
        verify(paymentConfirmService, never()).applyStatus(anyString(), any());
    }

    @Test
    void paidLinkIsAppliedInsteadOfCancelled() {
        pending(PaymentProvider.TINKOFF);
        bankSays("CONFIRMED", PaymentTransactionStatus.SUCCEEDED);

        service.releaseOwnReservations(limited, client);

        verify(paymentConfirmService).applyStatus("ext-TINKOFF", PaymentTransactionStatus.SUCCEEDED);
        verify(tinkoffService, never()).cancel(any());
        verify(paymentConfirmService, never()).supersede(anyString());
    }

    @Test
    void unknownBankStatusKeepsTheReservation() {
        pending(PaymentProvider.TINKOFF);
        bankSays("WEIRD", PaymentTransactionStatus.FAILED);

        service.releaseOwnReservations(limited, client);

        verify(tinkoffService, never()).cancel(any());
        verifyNoInteractions(paymentConfirmService);
    }

    @Test
    void yooKassaReservationsAndBankErrorsAreLeftAlone() {
        pending(PaymentProvider.YOOKASSA);
        service.releaseOwnReservations(limited, client);
        verifyNoInteractions(tinkoffService, paymentConfirmService);

        pending(PaymentProvider.TINKOFF);
        when(tinkoffService.getState("ext-TINKOFF")).thenThrow(new RuntimeException("bank down"));
        service.releaseOwnReservations(limited, client);
        verify(tinkoffService, never()).cancel(any());
        verifyNoInteractions(paymentConfirmService);
    }
}
