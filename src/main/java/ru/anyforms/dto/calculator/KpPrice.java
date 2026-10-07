package ru.anyforms.dto.calculator;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Цены для КП: статьи округлены до шага округления, итоги сложены из округлённых")
public record KpPrice(
        @Schema(description = "Разработка") double development,
        @Schema(description = "Цена одной формы") double formPrice,
        @Schema(description = "Тираж") int tirage,
        @Schema(description = "Формы итого = цена формы × тираж") double forms,
        @Schema(description = "Итого = разработка + формы") double total,
        @Schema(description = "Итого в пересчёте на одну форму") double perForm) {

    public static KpPrice zero(int tirage) {
        return new KpPrice(0, 0, tirage, 0, 0, 0);
    }
}
