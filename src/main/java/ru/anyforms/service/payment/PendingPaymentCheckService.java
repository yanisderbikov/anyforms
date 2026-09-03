package ru.anyforms.service.payment;

public interface PendingPaymentCheckService {

    /** Находит зависшие неоплаченные заказы розницы и ставит по ним таски в АМО; возвращает число поставленных. */
    int check();
}
