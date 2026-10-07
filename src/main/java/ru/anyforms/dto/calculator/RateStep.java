package ru.anyforms.dto.calculator;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Ступень шкалы: значение действует начиная с тиража from")
public record RateStep(
        @Schema(description = "Тираж от", example = "6") Integer from,
        @Schema(description = "Значение ступени", example = "950") Double value) {
}
