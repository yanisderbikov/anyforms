package ru.anyforms.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.anyforms.model.payment.PaymentTransaction;

import java.time.Instant;

@Schema(description = "Счёт на обучение (гайд/курс)")
public record TrainingInvoiceDTO(
        @Schema(description = "ID платежа в Юкассе") String externalPaymentId,
        @Schema(description = "GUIDE / COURSE / COURSE_PERSONAL") String productCode,
        String contactName,
        String contactPhone,
        String email,
        @Schema(description = "Сумма в копейках") Long amountKopecks,
        @Schema(description = "PENDING / SUCCEEDED / CANCELED / REFUNDED / FAILED") String status,
        String paymentUrl,
        String promoCode,
        Integer discountPercent,
        Instant createdAt
) {
    public static TrainingInvoiceDTO from(PaymentTransaction t) {
        return new TrainingInvoiceDTO(
                t.getExternalPaymentId(),
                t.getProductCode(),
                t.getContactName(),
                t.getContactPhone(),
                t.getEmail(),
                t.getAmount(),
                t.getStatus() != null ? t.getStatus().name() : null,
                t.getPaymentUrl(),
                t.getPromoCode(),
                t.getDiscountPercent(),
                t.getCreatedAt());
    }
}
