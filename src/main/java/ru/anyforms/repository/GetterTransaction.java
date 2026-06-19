package ru.anyforms.repository;

import ru.anyforms.model.payment.PaymentTransaction;

import java.util.Optional;
import java.util.UUID;

public interface GetterTransaction {
    Optional<PaymentTransaction> getByExternalPaymentId(UUID externalPaymentId);
}
