package ru.anyforms.dto.calculator;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "Итог заказа по выбранным вариантам позиций: КП, скидки и рентабельность")
public record OrderCalculationSummary(
        @Schema(description = "Разработка в КП до скидок") double developmentKp,
        @Schema(description = "Формы в КП до скидок") double formsKp,
        @Schema(description = "Цена по расчёту (КП до скидок)") double totalKp,
        @Schema(description = "Разработка после скидок") double developmentOffer,
        @Schema(description = "Формы после скидок") double formsOffer,
        @Schema(description = "Итого к оплате после скидок") double totalOffer,
        @Schema(description = "Скидка в рублях") double discountRub,
        @Schema(description = "Запрошенная скидка на формы, %") double formsDiscountRequested,
        @Schema(description = "Применённая скидка на формы, %") double formsDiscountApplied,
        @Schema(description = "Скидку на формы урезали до максимальной") boolean formsDiscountCapped,
        @Schema(description = "Скидка на разработку, %") double developmentDiscount,
        @Schema(description = "Промокод, %") double promoDiscount,
        @Schema(description = "Основатель разрешил маржу ниже минимума") boolean belowMinMarginAllowed,
        @Schema(description = "Затраты без налога") double cost,
        @Schema(description = "Прибыль") double profit,
        @Schema(description = "Маржа, доля; null — выручка 0") Double margin,
        @Schema(description = "Минимальная цена при минимальной марже") double minPrice,
        @Schema(description = "Максимальная скидка, ₽") double maxDiscountRub,
        @Schema(description = "Максимальная скидка от цены по расчёту, доля") double maxDiscountShare,
        @Schema(description = "Максимальная скидка, если давать её только на формы, доля") double maxFormsDiscountShare,
        @Schema(description = "Минимальная маржа") double minProfitShare,
        @Schema(description = "Ориентир маржи типового проекта") double targetProfitShare,
        @Schema(description = "Маржа ниже минимума") boolean belowMinMargin,
        @Schema(description = "Цены КП действительны, дней") int offerValidityDays) {
}
