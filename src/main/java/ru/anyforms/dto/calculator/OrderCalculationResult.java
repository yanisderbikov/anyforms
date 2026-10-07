package ru.anyforms.dto.calculator;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Результат расчёта заказа")
public record OrderCalculationResult(
        List<PositionCalculation> positions,
        OrderCalculationSummary summary,
        @Schema(description = "Мало данных — оформлять как «Предварительная оценка»") boolean preliminary,
        @Schema(description = "Есть поля, посчитанные оценкой и требующие подтверждения") boolean hasEstimates,
        @Schema(description = "Есть исключения основателя") boolean hasExceptions,
        List<CalculationHint> hints,
        @Schema(description = "Версия ставок, по которой посчитано") Long ratesVersionId,
        Instant ratesUpdatedAt) {

    public OrderCalculationResult withoutBreakdown() {
        return new OrderCalculationResult(positions.stream().map(PositionCalculation::withoutBreakdown).toList(),
                summary, preliminary, hasEstimates, hasExceptions, hints, ratesVersionId, ratesUpdatedAt);
    }
}
