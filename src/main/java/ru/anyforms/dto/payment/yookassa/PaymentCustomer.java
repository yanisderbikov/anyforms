package ru.anyforms.dto.payment.yookassa;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentCustomer {

    @JsonProperty("full_name")
    private String fullName;

    @JsonProperty("email")
    private String email;
}
