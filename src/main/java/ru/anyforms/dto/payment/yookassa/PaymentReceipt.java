package ru.anyforms.dto.payment.yookassa;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentReceipt {

    @JsonProperty("customer")
    private PaymentCustomer customer;

    @JsonProperty("items")
    private List<PaymentItem> items;
}
