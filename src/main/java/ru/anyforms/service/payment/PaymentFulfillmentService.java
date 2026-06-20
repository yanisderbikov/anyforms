package ru.anyforms.service.payment;

import ru.anyforms.model.payment.PaymentTransaction;

public interface PaymentFulfillmentService {
    void fulfill(PaymentTransaction transaction);
}
