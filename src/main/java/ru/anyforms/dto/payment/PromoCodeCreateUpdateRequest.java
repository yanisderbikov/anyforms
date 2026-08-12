package ru.anyforms.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Создание/обновление промокода")
public class PromoCodeCreateUpdateRequest {

    @NotBlank(message = "Код промокода обязателен")
    @Size(max = 64, message = "Код не длиннее 64 символов")
    private String code;

    @NotNull(message = "Процент обязателен (0 — скидка только суммой)")
    @Min(value = 0, message = "Процент не может быть меньше 0")
    @Max(value = 100, message = "Процент не может быть больше 100")
    private Integer discountPercent;

    @Positive(message = "Фиксированная скидка должна быть больше нуля")
    private Long discountAmountKopecks;

    @Positive(message = "Минимальная сумма должна быть больше нуля")
    private Long minOrderKopecks;

    @NotNull(message = "Поле active обязательно")
    private Boolean active;

    /** ISO-8601, например 2026-08-31T21:00:00Z; null — без нижней границы. */
    private String validFrom;

    /** ISO-8601, исключительная граница; null — бессрочно. */
    private String validUntil;
}
