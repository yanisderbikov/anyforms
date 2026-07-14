package ru.anyforms.dto.payment.tinkoff;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TinkoffCancelResponse {

    @JsonProperty("Success")
    private Boolean success;

    @JsonProperty("ErrorCode")
    private String errorCode;

    @JsonProperty("Message")
    private String message;

    @JsonProperty("Details")
    private String details;

    @JsonProperty("TerminalKey")
    private String terminalKey;

    /** Статус после отмены: CANCELED / REVERSED / REFUNDED / PARTIAL_REFUNDED и т.д. */
    @JsonProperty("Status")
    private String status;

    @JsonProperty("PaymentId")
    private String paymentId;

    @JsonProperty("OrderId")
    private String orderId;

    /** Сумма до возврата, в копейках. */
    @JsonProperty("OriginalAmount")
    private Long originalAmount;

    /** Остаток после возврата, в копейках. */
    @JsonProperty("NewAmount")
    private Long newAmount;
}
