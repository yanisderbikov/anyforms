package ru.anyforms.service.payment.impl;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import ru.anyforms.dto.payment.tinkoff.TinkoffCancelRequest;
import ru.anyforms.dto.payment.tinkoff.TinkoffCancelResponse;
import ru.anyforms.dto.payment.tinkoff.TinkoffGetStateResponse;
import ru.anyforms.model.payment.PromoCode;
import ru.anyforms.repository.GetterTransaction;
import ru.anyforms.service.payment.PaymentConfirmService;
import ru.anyforms.service.payment.PaymentStatusConverter;
import ru.anyforms.service.payment.TinkoffService;
import ru.anyforms.service.promo.PromoClient;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    private static final String PAYMENT = "ext-1";

    private final GetterTransaction getterTransaction = mock(GetterTransaction.class);
    private final TinkoffService tinkoffService = mock(TinkoffService.class);
    private final PaymentConfirmService paymentConfirmService = mock(PaymentConfirmService.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final PromoReservationServiceImpl service = new PromoReservationServiceImpl(getterTransaction,
            tinkoffService, new PaymentStatusConverter(), paymentConfirmService, transactionManager);

    private final PromoCode limited = PromoCode.builder().id(UUID.randomUUID()).code("ONE-AAAAA").maxUses(1).build();
    private final PromoClient client = PromoClient.of("buyer@example.com", "+79991234567", DEVICE);

    private void pendingLink() {
        when(getterTransaction.getPendingTinkoffPaymentIds(eq("ONE-AAAAA"), eq(DEVICE), any())).thenReturn(List.of(PAYMENT));
    }

    private void bankSays(String status) {
        TinkoffGetStateResponse state = new TinkoffGetStateResponse();
        state.setStatus(status);
        when(tinkoffService.getState(PAYMENT)).thenReturn(state);
    }

    private void cancelAnswers(String status) {
        TinkoffCancelResponse response = new TinkoffCancelResponse();
        response.setStatus(status);
        when(tinkoffService.cancel(any())).thenReturn(response);
    }

    @Test
    void unlimitedCodesAndClientsWithoutDeviceAreIgnored() {
        assertFalse(service.releaseOwnReservations(PromoCode.builder().code("ANY-10").build(), client));
        assertFalse(service.releaseOwnReservations(limited, PromoClient.of(null, null, null)));
        assertFalse(service.releaseOwnReservations(limited, PromoClient.of("buyer@example.com", "+79991234567", null)));

        verifyNoInteractions(getterTransaction, tinkoffService, paymentConfirmService);
    }

    @Test
    void untouchedLinkIsCancelledAndSupersededInItsOwnTransaction() {
        pendingLink();
        bankSays("FORM_SHOWED");
        cancelAnswers("CANCELED");
        when(paymentConfirmService.supersede(PAYMENT)).thenReturn(true);

        assertTrue(service.releaseOwnReservations(limited, client));

        ArgumentCaptor<TinkoffCancelRequest> cancel = ArgumentCaptor.forClass(TinkoffCancelRequest.class);
        verify(tinkoffService).cancel(cancel.capture());
        assertEquals(PAYMENT, cancel.getValue().getPaymentId());
        verify(paymentConfirmService).supersede(PAYMENT);
        ArgumentCaptor<TransactionDefinition> tx = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager).getTransaction(tx.capture());
        assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW, tx.getValue().getPropagationBehavior());
        verify(paymentConfirmService, never()).applyStatus(anyString(), any());
    }

    @Test
    void paymentInProgressOrPaidIsNeverCancelledNorAppliedHere() {
        pendingLink();
        for (String status : List.of("AUTHORIZING", "3DS_CHECKING", "AUTHORIZED", "CONFIRMING", "CONFIRMED", "REJECTED")) {
            bankSays(status);

            assertFalse(service.releaseOwnReservations(limited, client));
        }

        verify(tinkoffService, never()).cancel(any());
        verifyNoInteractions(paymentConfirmService);
    }

    @Test
    void cancelThatEndsInReversalLeavesTheStatusToTheNotification() {
        pendingLink();
        bankSays("NEW");
        cancelAnswers("REVERSED");

        assertFalse(service.releaseOwnReservations(limited, client));

        verifyNoInteractions(paymentConfirmService);
    }

    @Test
    void bankErrorsKeepTheReservation() {
        pendingLink();
        when(tinkoffService.getState(PAYMENT)).thenThrow(new RuntimeException("bank down"));

        assertFalse(service.releaseOwnReservations(limited, client));

        verify(tinkoffService, never()).cancel(any());
        verifyNoInteractions(paymentConfirmService);
    }
}
