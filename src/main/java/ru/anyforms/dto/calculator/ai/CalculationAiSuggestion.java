package ru.anyforms.dto.calculator.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import ru.anyforms.dto.calculator.CalculationVariantRequest;

import java.util.Map;

@Builder
@Schema(description = "Подсказка AI: предложенные значения полей. Все значения — оценка, требуют подтверждения")
public record CalculationAiSuggestion(
        @Schema(description = "Габариты, если AI смог их определить по референсам") Double widthMm,
        Double depthMm,
        Double heightMm,
        @Schema(description = "Тип формы и технические поля; null — AI не предлагает значение") CalculationVariantRequest variant,
        @Schema(description = "Пояснения по полям: имя поля → почему такое значение") Map<String, String> notes,
        @Schema(description = "Общий комментарий AI") String summary,
        @Schema(description = "Кто ответил: провайдер и модель") String provider) {
}
