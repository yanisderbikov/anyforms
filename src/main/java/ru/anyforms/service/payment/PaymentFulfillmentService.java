package ru.anyforms.service.payment;

import ru.anyforms.model.payment.PaymentTransaction;

/** Выдача продукта после успешной оплаты (например, постановка письма в очередь). */
public interface PaymentFulfillmentService {
    void fulfill(PaymentTransaction transaction);
}
