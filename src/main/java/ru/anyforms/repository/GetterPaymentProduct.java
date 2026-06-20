package ru.anyforms.repository;

import ru.anyforms.model.payment.PaymentProduct;

import java.util.List;
import java.util.Optional;

public interface GetterPaymentProduct {
    Optional<PaymentProduct> getByCode(String code);
    List<PaymentProduct> getAllActive();
}
