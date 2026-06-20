package ru.anyforms.dto.payment.yookassa;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentConfirmation {

    @JsonProperty("type")
    private String type;

    @JsonProperty("return_url")
    private String returnUrl;
}
