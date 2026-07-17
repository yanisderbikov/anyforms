package ru.anyforms.repository;

import ru.anyforms.model.payment.PaymentTransaction;

import java.util.List;
import java.util.Optional;

public interface GetterTransaction {
    Optional<PaymentTransaction> getByExternalPaymentId(String externalPaymentId);

    List<PaymentTransaction> getByOrderId(Long orderId);

    boolean promoUsedByCustomer(String promoCode, String email, String phoneLast10);
}
