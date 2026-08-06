package ru.anyforms.service.payment;

import ru.anyforms.model.payment.PaymentTransaction;

public interface GuideAmoLeadService {

    void pushGuidePurchase(PaymentTransaction transaction);
}
