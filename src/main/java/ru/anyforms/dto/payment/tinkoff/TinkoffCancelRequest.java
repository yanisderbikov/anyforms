package ru.anyforms.dto.payment.tinkoff;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TinkoffCancelRequest {

    @JsonProperty("TerminalKey")
    private String terminalKey;

    @JsonProperty("PaymentId")
    private String paymentId;

    @JsonProperty("Token")
    private String token;

    /** Сумма возврата в копейках; null — полный возврат. */
    @JsonProperty("Amount")
    private Long amount;
}
