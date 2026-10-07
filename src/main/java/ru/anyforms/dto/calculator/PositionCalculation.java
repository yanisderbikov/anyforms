package ru.anyforms.dto.calculator;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Расчёт позиции: все сочетания вариантов и выбранное для итога заказа")
public record PositionCalculation(
        int index,
        String productName,
        boolean digitalOnly,
        boolean bonus,
        @Schema(description = "Мало данных — показывать как «Предварительную оценку»") boolean preliminary,
        List<CalculationOption> options,
        @Schema(description = "Индекс варианта в options, который идёт в итог заказа") int selectedOption,
        List<CalculationHint> hints) {

    public CalculationOption selected() {
        return options.get(selectedOption);
    }

    public PositionCalculation withoutBreakdown() {
        return new PositionCalculation(index, productName, digitalOnly, bonus, preliminary,
                options.stream().map(CalculationOption::withoutBreakdown).toList(), selectedOption, hints);
    }
}
