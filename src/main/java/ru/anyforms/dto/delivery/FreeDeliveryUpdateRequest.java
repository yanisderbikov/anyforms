package ru.anyforms.dto.delivery;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Изменение настройки бесплатной доставки")
public class FreeDeliveryUpdateRequest {

    @NotNull(message = "Поле enabled обязательно")
    private Boolean enabled;

    @NotNull(message = "Укажите сумму, от которой доставка бесплатная")
    @Positive(message = "Сумма должна быть больше нуля")
    @Max(value = 100_000_000_00L, message = "Сумма слишком большая")
    @Schema(description = "Порог суммы к оплате, копейки", example = "1200000")
    private Long thresholdKopecks;
}
