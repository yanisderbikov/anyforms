package ru.anyforms.service.payment;

import org.springframework.stereotype.Component;
import ru.anyforms.model.payment.PaymentTransactionStatus;

/** Маппинг статуса платежа Юкассы в наш {@link PaymentTransactionStatus}. */
@Component
public class PaymentStatusConverter {

    public PaymentTransactionStatus fromYooKassa(String status) {
        if (status == null) {
            return PaymentTransactionStatus.FAILED;
        }
        return switch (status.toLowerCase()) {
            case "pending", "waiting_for_capture" -> PaymentTransactionStatus.PENDING;
            case "succeeded" -> PaymentTransactionStatus.SUCCEEDED;
            case "canceled" -> PaymentTransactionStatus.CANCELED;
            default -> PaymentTransactionStatus.FAILED;
        };
    }
}
