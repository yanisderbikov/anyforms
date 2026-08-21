package ru.anyforms.service.payment;

import ru.anyforms.model.payment.PaymentTransaction;

/**
 * Уведомляет менеджеров в амо о неуспешной оплате: сделка в воронке продукта
 * (обучение или маркетплейс) плюс задача «Пропущенное» на ответственного.
 */
public interface FailedPaymentNotificationService {

    void notify(PaymentTransaction transaction);
}
