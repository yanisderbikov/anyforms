package ru.anyforms.dto.payment;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PurchaseRequest {

    /** Код продукта из {@link ru.anyforms.model.payment.PaymentProduct}, например {@code GUIDE}. */
    @NotBlank
    @JsonProperty("productCode")
    private String productCode;

    @NotBlank
    @Email
    @JsonProperty("email")
    private String email;

    /** ФИО для чека (необязательно). */
    @JsonProperty("fullName")
    private String fullName;
}
