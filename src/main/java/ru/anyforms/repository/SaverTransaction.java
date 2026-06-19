package ru.anyforms.repository;

import ru.anyforms.model.payment.PaymentTransaction;

public interface SaverTransaction {
    PaymentTransaction save(PaymentTransaction transaction);
}
