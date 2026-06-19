package ru.anyforms.dto.payment.yookassa;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.anyforms.dto.payment.Amount;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentItem {

    @JsonProperty("description")
    private String description;

    @JsonProperty("amount")
    private Amount amount;

    @JsonProperty("vat_code")
    private Integer vatCode;

    @JsonProperty("quantity")
    private Integer quantity;
}
