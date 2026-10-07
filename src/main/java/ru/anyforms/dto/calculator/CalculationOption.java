package ru.anyforms.dto.calculator;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.anyforms.model.calculator.FormType;
import ru.anyforms.model.calculator.SiliconeType;

import java.util.List;
import java.util.Map;

@Schema(description = "Одна колонка сравнения: вариант формы × силикон × тираж")
public record CalculationOption(
        @Schema(description = "Индекс варианта формы в позиции") int variantIndex,
        FormType formType,
        SiliconeType silicone,
        int tirage,
        @Schema(description = "Параметры после подстановки оценок") PriceInput input,
        @Schema(description = "Откуда взялись незаполненные поля: оценка или значение по умолчанию") Map<String, ParameterSource> sources,
        @Schema(description = "Разбивка цены — только основателю") PriceBreakdown price,
        @Schema(description = "Цены для КП до скидок") KpPrice kp,
        @Schema(description = "Цены для КП после скидок заказа") KpPrice offer,
        @Schema(description = "Себестоимость по статьям — только основателю") CostBreakdown cost,
        @Schema(description = "Маржа варианта после скидок, доля") Double margin,
        List<CalculationHint> hints,
        @Schema(description = "Работы разработки в этом варианте (без сумм) — для списка работ в КП") List<String> developmentWorks) {

    public CalculationOption withoutBreakdown() {
        return new CalculationOption(variantIndex, formType, silicone, tirage, input, sources, null, kp, offer, null,
                margin, hints, developmentWorks);
    }
}
