package ru.anyforms.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentUrlResponse {
    private UUID externalPaymentId;
    private String paymentUrl;
    private Amount amount;
}
