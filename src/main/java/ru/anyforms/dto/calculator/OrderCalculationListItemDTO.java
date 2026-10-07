package ru.anyforms.dto.calculator;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.anyforms.model.calculator.OrderCalculation;

import java.time.Instant;

@Schema(description = "Запись журнала расчётов")
public record OrderCalculationListItemDTO(
        Long id,
        Instant createdAt,
        String createdByName,
        String createdByEmail,
        String client,
        @Schema(description = "Изделия расчёта") String title,
        @Schema(description = "Итого к оплате после скидок, ₽") Double totalRub,
        @Schema(description = "Маржа, доля") Double margin,
        boolean preliminary,
        boolean hasEstimates,
        boolean hasExceptions,
        boolean belowMinMargin,
        String comment,
        Long ratesVersionId) {

    public static OrderCalculationListItemDTO from(OrderCalculation calculation) {
        return new OrderCalculationListItemDTO(
                calculation.getId(),
                calculation.getCreatedAt(),
                calculation.getCreatedByName(),
                calculation.getCreatedByEmail(),
                calculation.getClient(),
                calculation.getTitle(),
                calculation.getTotalRub(),
                calculation.getMargin(),
                calculation.isPreliminary(),
                calculation.isHasEstimates(),
                calculation.isHasExceptions(),
                calculation.isBelowMinMargin(),
                calculation.getComment(),
                calculation.getRatesId());
    }
}
