package ru.anyforms.dto.calculator;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "Разбивка цены по шагам 5.1–5.15 регламента, ₽ с копейками без округления для КП")
public record PriceBreakdown(
        @Schema(description = "5.1 Моделирование") double model,
        @Schema(description = "5.2 Расход смолы, мл") double slaMl,
        @Schema(description = "5.3 Печать SLA") double sla,
        @Schema(description = "5.4 Проектирование оснастки") double fdmProject,
        @Schema(description = "5.5 Литьевой комплект") double kit,
        @Schema(description = "5.6 Обработка") double processing,
        @Schema(description = "5.6 ЧПУ") double cnc,
        @Schema(description = "5.7 Подготовка") double prep,
        @Schema(description = "5.8 Промежуточная оловянная форма") double tin,
        @Schema(description = "5.9 Пластиковая копия") double copy,
        @Schema(description = "5.10 Производственных комплектов нужно") int kits,
        @Schema(description = "5.10 Комплектов оплачивает клиент") int kitsPaid,
        @Schema(description = "5.10 Доплата за дополнительные комплекты") double extraKits,
        @Schema(description = "5.11 Разработка итого") double development,
        @Schema(description = "5.12 Вес силикона с запасом, г") double siliconeWeight,
        @Schema(description = "5.12 Силикон") double siliconeCost,
        @Schema(description = "5.12 Рабочая оснастка") double shellCost,
        @Schema(description = "5.12 Печать рабочей оснастки") double shellPrint,
        @Schema(description = "5.12 Отливка") double pour,
        @Schema(description = "5.12 Разрез") double cut,
        @Schema(description = "5.12 Доплаты к форме") double extra,
        @Schema(description = "5.12 Расчётная цена формы") double formCalc,
        @Schema(description = "5.13 Минимум цены формы по тиражу") double minFormPrice,
        @Schema(description = "5.13 Цена формы") double formPrice,
        @Schema(description = "Цена формы определена минимумом по тиражу") boolean minPriceApplied,
        @Schema(description = "Цена формы задана вручную") boolean formPriceOverridden,
        @Schema(description = "5.14 Формы итого") double formsTotal,
        @Schema(description = "5.14 Сработала защита от обрыва") boolean cliffApplied,
        @Schema(description = "5.14 Нижняя граница по предыдущей ступени") double cliffFloor,
        @Schema(description = "5.15 Итого позиции") double total) {
}
