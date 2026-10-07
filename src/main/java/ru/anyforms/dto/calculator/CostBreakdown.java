package ru.anyforms.dto.calculator;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Полная себестоимость варианта без налога (раздел 8 регламента)")
public record CostBreakdown(
        @Schema(description = "Модель у художника") double contractor,
        @Schema(description = "Смола и мойка") double resin,
        @Schema(description = "PETG литьевых комплектов") double kitPlastic,
        @Schema(description = "Промежуточная форма и копии") double intermediate,
        @Schema(description = "Материалы форм на весь тираж") double forms,
        @Schema(description = "Часы работы") double hours,
        @Schema(description = "Часы × (ФОТ + постоянные)") double labor,
        @Schema(description = "Затраты без налога") double total) {
}
