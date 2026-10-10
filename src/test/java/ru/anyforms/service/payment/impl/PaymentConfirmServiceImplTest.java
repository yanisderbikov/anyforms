package ru.anyforms.service.payment.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import ru.anyforms.model.payment.PaymentTransaction;
import ru.anyforms.model.payment.PaymentTransactionStatus;
import ru.anyforms.repository.GetterTransaction;
import ru.anyforms.repository.SaverTransaction;
import ru.anyforms.service.payment.PaymentFulfillmentService;
import ru.anyforms.service.payment.PaymentStatusConverter;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentConfirmServiceImplTest {

    private final GetterTransaction getterTransaction = mock(GetterTransaction.class);
    private final SaverTransaction saverTransaction = mock(SaverTransaction.class);
    private final PaymentFulfillmentService fulfillmentService = mock(PaymentFulfillmentService.class);
    private final PaymentConfirmServiceImpl service = new PaymentConfirmServiceImpl(
            getterTransaction,
            saverTransaction,
            new PaymentStatusConverter(),
            fulfillmentService,
            mock(TinkoffTokenService.class),
            new ObjectMapper());

    @Test
    void statusTransitionLoadsTransactionForUpdateBeforeApplyingSideEffects() {
        PaymentTransaction transaction = PaymentTransaction.builder()
                .status(PaymentTransactionStatus.PENDING)
                .externalPaymentId("payment-1")
                .build();
        when(getterTransaction.getByExternalPaymentIdForUpdate("payment-1"))
                .thenReturn(Optional.of(transaction));

        assertTrue(service.applyStatus("payment-1", PaymentTransactionStatus.CANCELED));

        verify(getterTransaction).getByExternalPaymentIdForUpdate("payment-1");
        verify(saverTransaction).save(transaction);
        verify(fulfillmentService).cancel(transaction);
    }

    @Test
    void supersedeDoesNotOverwriteAStatusChangedBeforeItAcquiresTheLock() {
        PaymentTransaction transaction = PaymentTransaction.builder()
                .status(PaymentTransactionStatus.SUCCEEDED)
                .externalPaymentId("payment-1")
                .build();
        when(getterTransaction.getByExternalPaymentIdForUpdate("payment-1"))
                .thenReturn(Optional.of(transaction));

        assertFalse(service.supersede("payment-1"));

        verify(getterTransaction).getByExternalPaymentIdForUpdate("payment-1");
    }
}
