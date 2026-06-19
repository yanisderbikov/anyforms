package ru.anyforms.dto.payment;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class YooKassaWebhookBody {

    @JsonProperty("type")
    private String type;

    @JsonProperty("event")
    private String event;

    @JsonProperty("object")
    private PaymentData data;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PaymentData {

        @JsonProperty("id")
        private UUID id;

        @JsonProperty("status")
        private String status;

        @JsonProperty("amount")
        private Amount amount;

        @JsonProperty("income_amount")
        private Amount incomeAmount;
    }
}
