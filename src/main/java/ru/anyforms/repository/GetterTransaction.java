package ru.anyforms.repository;

import ru.anyforms.model.payment.PaymentTransaction;

import java.util.Optional;

public interface GetterTransaction {
    Optional<PaymentTransaction> getByExternalPaymentId(String externalPaymentId);
}
