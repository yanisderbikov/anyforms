package ru.anyforms.dto.payment.tinkoff;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class TinkoffNotification {

    @JsonProperty("TerminalKey")
    private String terminalKey;

    @JsonProperty("OrderId")
    private String orderId;

    @JsonProperty("Success")
    private Boolean success;

    @JsonProperty("Status")
    private String status;

    @JsonProperty("PaymentId")
    private Long paymentId;

    @JsonProperty("ErrorCode")
    private String errorCode;

    /** Сумма в копейках. */
    @JsonProperty("Amount")
    private Long amount;

    @JsonProperty("CardId")
    private Long cardId;

    @JsonProperty("Pan")
    private String pan;

    @JsonProperty("ExpDate")
    private String expDate;

    @JsonProperty("RebillId")
    private String rebillId;

    @JsonProperty("Token")
    private String token;
}
