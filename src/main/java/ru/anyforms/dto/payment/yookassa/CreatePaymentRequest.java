package ru.anyforms.dto.payment.yookassa;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.anyforms.dto.payment.Amount;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CreatePaymentRequest {

    @JsonProperty("amount")
    private Amount amount;

    @JsonProperty("description")
    private String description;

    @JsonProperty("confirmation")
    private PaymentConfirmation confirmation;

    @JsonProperty("capture")
    private boolean capture;

    @JsonProperty("receipt")
    private PaymentReceipt receipt;

    @JsonProperty("payment_mode")
    private String paymentMode;

    @JsonProperty("payment_subject")
    private String paymentSubject;
}
