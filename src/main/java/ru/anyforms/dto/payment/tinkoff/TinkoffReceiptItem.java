package ru.anyforms.dto.payment.tinkoff;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TinkoffReceiptItem {

    @JsonProperty("Name")
    private String name;

    /** Цена за единицу в копейках. */
    @JsonProperty("Price")
    private Long price;

    @JsonProperty("Quantity")
    private Integer quantity;

    /** Сумма позиции в копейках (Price × Quantity). */
    @JsonProperty("Amount")
    private Long amount;

    /** Ставка НДС: none, vat0, vat10, vat20 и т.д. */
    @JsonProperty("Tax")
    private String tax;

    @JsonProperty("PaymentMethod")
    private String paymentMethod;

    @JsonProperty("PaymentObject")
    private String paymentObject;
}
