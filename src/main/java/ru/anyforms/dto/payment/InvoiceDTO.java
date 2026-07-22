package ru.anyforms.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.anyforms.model.payment.PaymentTransaction;

import java.time.Instant;

@Schema(description = "Выставленный вручную счёт")
public record InvoiceDTO(
        @Schema(description = "ID платежа в Т-Кассе") String externalPaymentId,
        String contactName,
        String contactPhone,
        String email,
        @Schema(description = "Сумма в копейках") Long amountKopecks,
        @Schema(description = "PENDING / SUCCEEDED / CANCELED / REFUNDED / FAILED") String status,
        String paymentUrl,
        String description,
        Instant createdAt
) {
    public static InvoiceDTO from(PaymentTransaction t) {
        return new InvoiceDTO(
                t.getExternalPaymentId(),
                t.getContactName(),
                t.getContactPhone(),
                t.getEmail(),
                t.getAmount(),
                t.getStatus() != null ? t.getStatus().name() : null,
                t.getPaymentUrl(),
                t.getDescription(),
                t.getCreatedAt());
    }
}
