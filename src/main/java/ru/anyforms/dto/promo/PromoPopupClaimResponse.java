package ru.anyforms.dto.promo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Выданный персональный промокод")
public record PromoPopupClaimResponse(
        String code,
        @Schema(description = "Окончание действия (исключительно), ISO-8601") String validUntil,
        Integer discountPercent,
        Long discountAmountKopecks,
        Long minOrderKopecks,
        @Schema(description = "true — код уже выдавался этому контакту раньше") boolean repeated
) {
}
