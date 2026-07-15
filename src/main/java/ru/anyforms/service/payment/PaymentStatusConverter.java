package ru.anyforms.service.payment;

import org.springframework.stereotype.Component;
import ru.anyforms.model.payment.PaymentTransactionStatus;

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

    public PaymentTransactionStatus fromTinkoff(String status) {
        if (status == null) {
            return PaymentTransactionStatus.FAILED;
        }
        return switch (status.toUpperCase()) {
            case "NEW", "FORM_SHOWED", "AUTHORIZING", "3DS_CHECKING", "3DS_CHECKED",
                 "PAY_CHECKING", "AUTHORIZED", "CONFIRMING", "CHECKING", "CHECKED",
                 "COMPLETING" -> PaymentTransactionStatus.PENDING;
            case "CONFIRMED", "COMPLETED" -> PaymentTransactionStatus.SUCCEEDED;
            // Возвраты: деньги списывались и вернулись покупателю (REVERSED — отмена
            // авторизации до списания, для покупателя это тот же возврат холда).
            case "REVERSING", "REVERSED", "REFUNDING", "REFUNDED",
                 "PARTIAL_REFUNDED" -> PaymentTransactionStatus.REFUNDED;
            // Неуспешная оплата: деньги не списывались.
            case "CANCELED", "DEADLINE_EXPIRED", "REJECTED", "AUTH_FAIL",
                 "ATTEMPTS_EXPIRED" -> PaymentTransactionStatus.CANCELED;
            default -> PaymentTransactionStatus.FAILED;
        };
    }
}
