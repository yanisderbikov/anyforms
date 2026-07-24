package ru.anyforms.dto.payment.yookassa;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentCustomer {

    @JsonProperty("full_name")
    private String fullName;

    @JsonProperty("email")
    private String email;

    /** Телефон для чека в формате ITU-T E.164 (например {@code 79001234567}); {@code null} — не передаётся. */
    @JsonProperty("phone")
    private String phone;
}
