package ru.anyforms.service.payment;

import ru.anyforms.dto.payment.YooKassaWebhookBody;
import ru.anyforms.model.payment.PaymentTransactionStatus;

public interface PaymentConfirmService {
    boolean confirm(YooKassaWebhookBody webhookBody);

    boolean confirmTinkoff(String rawNotificationBody);

    /**
     * Применить статус к транзакции по внешнему ID платежа тем же путём, что и вебхук:
     * с защитой от отката статуса и с фулфилментом/отменой при переходе.
     */
    boolean applyStatus(String externalPaymentId, PaymentTransactionStatus newStatus);
}
