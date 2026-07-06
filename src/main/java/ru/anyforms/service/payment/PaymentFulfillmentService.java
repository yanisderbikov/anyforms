package ru.anyforms.service.payment;

import ru.anyforms.model.payment.PaymentTransaction;

public interface PaymentFulfillmentService {

    /** Платёж прошёл: выдать продукт (письмо; для маркетплейса — перевести заказ в PAID + лид + чек). */
    void fulfill(PaymentTransaction transaction);

    /** Платёж отменён: для маркетплейса пометить заказ CANCELED; для курса/гайда — ничего. */
    void cancel(PaymentTransaction transaction);
}
