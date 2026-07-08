package ru.anyforms.dto.payment;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PromoCheckResponse {

    @JsonProperty("valid")
    private boolean valid;

    /** Нормализованный код (верхний регистр); null, если код не найден. */
    @JsonProperty("code")
    private String code;

    @JsonProperty("discountPercent")
    private Integer discountPercent;

    /** Цена продукта без скидки, в копейках. */
    @JsonProperty("priceKopecks")
    private Long priceKopecks;

    /** Цена со скидкой, в копейках; null, если код невалиден. */
    @JsonProperty("discountedPriceKopecks")
    private Long discountedPriceKopecks;

    /** Причина отказа для показа пользователю; null, если код валиден. */
    @JsonProperty("message")
    private String message;

    /**
     * Момент окончания действия кода (исключительно) в ISO-8601, например
     * {@code 2026-07-30T21:00:00Z}; null — бессрочный или код невалиден.
     * Строка, а не Instant: иначе Jackson может отдать epoch-секунды,
     * которые фронт неверно прочитает как миллисекунды.
     */
    @JsonProperty("validUntil")
    private String validUntil;
}
