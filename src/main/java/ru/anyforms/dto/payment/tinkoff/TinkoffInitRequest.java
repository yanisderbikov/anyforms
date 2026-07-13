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
public class TinkoffInitRequest {

    @JsonProperty("TerminalKey")
    private String terminalKey;

    /** Сумма в копейках. */
    @JsonProperty("Amount")
    private Long amount;

    /** Уникальный идентификатор заказа в системе мерчанта. */
    @JsonProperty("OrderId")
    private String orderId;

    @JsonProperty("Description")
    private String description;

    /** O — одностадийный платёж, T — двухстадийный. */
    @JsonProperty("PayType")
    private String payType;

    @JsonProperty("SuccessURL")
    private String successURL;

    @JsonProperty("FailURL")
    private String failURL;

    @JsonProperty("NotificationURL")
    private String notificationURL;

    @JsonProperty("Receipt")
    private TinkoffReceipt receipt;

    /** SHA-256 подпись запроса. */
    @JsonProperty("Token")
    private String token;
}
