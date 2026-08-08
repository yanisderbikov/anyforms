package ru.anyforms.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.anyforms.model.payment.PaymentTransaction;

import java.time.Instant;

@Schema(description = "Оплаченная через Юкассу покупка гайда/курса")
public record ReceiptTransactionDTO(
        @Schema(description = "ID платежа в Юкассе") String externalPaymentId,
        @Schema(description = "GUIDE / COURSE / COURSE_PERSONAL") String productCode,
        @Schema(description = "Название продукта из payment_product") String productTitle,
        String contactName,
        String email,
        @Schema(description = "Сумма в копейках") Long amountKopecks,
        @Schema(description = "Время оплаты (последнее обновление транзакции)") Instant paidAt,
        @Schema(description = "Чек по этому email и продукту уже отправляли") boolean receiptSent
) {
    public static ReceiptTransactionDTO from(PaymentTransaction t, String productTitle, boolean receiptSent) {
        return new ReceiptTransactionDTO(
                t.getExternalPaymentId(),
                t.getProductCode(),
                productTitle,
                t.getContactName(),
                t.getEmail(),
                t.getAmount(),
                t.getUpdatedAt(),
                receiptSent);
    }
}
