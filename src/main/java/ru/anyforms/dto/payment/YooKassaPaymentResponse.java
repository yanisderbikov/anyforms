package ru.anyforms.dto.payment;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class YooKassaPaymentResponse {

    @JsonProperty("id")
    private UUID id;

    @JsonProperty("status")
    private String status;

    @JsonProperty("amount")
    private Amount amount;

    @JsonProperty("description")
    private String description;

    @JsonProperty("created_at")
    private OffsetDateTime createdAt;

    @JsonProperty("confirmation")
    private Confirmation confirmation;

    @JsonProperty("test")
    private Boolean test;

    @JsonProperty("paid")
    private Boolean paid;

    @JsonProperty("refundable")
    private Boolean refundable;

    @JsonProperty("metadata")
    private Map<String, Object> metadata;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Confirmation {

        @JsonProperty("type")
        private String type;

        @JsonProperty("confirmation_url")
        private String confirmationUrl;
    }
}
