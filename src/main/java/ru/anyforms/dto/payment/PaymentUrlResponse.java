package ru.anyforms.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentUrlResponse {
    private String externalPaymentId;
    private String paymentUrl;
    private Amount amount;
}
