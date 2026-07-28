package ru.anyforms.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.anyforms.model.payment.PaymentTransaction;

import java.time.Instant;

@Schema(description = "Оплаченная через Юкассу покупка гайда/курса")
public record ReceiptTransactionDTO(
        @Schema(description = "ID платежа в Юкассе") String externalPaymentId,
        @Schema(description = "GUIDE / COURSE / COURSE_PERSONAL") String productCode,
        String contactName,
        String email,
        @Schema(description = "Сумма в копейках") Long amountKopecks,
        @Schema(description = "Время оплаты (последнее обновление транзакции)") Instant paidAt
) {
    public static ReceiptTransactionDTO from(PaymentTransaction t) {
        return new ReceiptTransactionDTO(
                t.getExternalPaymentId(),
                t.getProductCode(),
                t.getContactName(),
                t.getEmail(),
                t.getAmount(),
                t.getUpdatedAt());
    }
}
