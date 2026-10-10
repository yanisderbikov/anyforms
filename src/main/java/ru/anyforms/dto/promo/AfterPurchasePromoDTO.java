package ru.anyforms.dto.promo;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.anyforms.model.payment.PromoCode;
import ru.anyforms.model.promo.PromoPopup;

import java.util.UUID;

@Schema(description = "Промокод на следующий заказ, который показываем после оплаты")
public record AfterPurchasePromoDTO(
        UUID popupId,
        @Schema(description = "Внутреннее название попапа — для аналитики") String popupName,
        String title,
        String description,
        String buttonText,
        String code,
        @Schema(description = "Окончание действия кода (исключительно), ISO-8601") String validUntil,
        Integer discountPercent,
        Long discountAmountKopecks,
        Long minOrderKopecks,
        Integer codeTtlDays,
        @Schema(description = "true — код по этому заказу уже выдавали раньше") boolean repeated
) {
    public static AfterPurchasePromoDTO of(PromoPopup popup, PromoCode promo, boolean repeated) {
        return new AfterPurchasePromoDTO(
                popup.getId(),
                popup.getName(),
                popup.getTitle(),
                popup.getDescription(),
                popup.getButtonText(),
                promo.getCode(),
                promo.getValidUntil() != null ? promo.getValidUntil().toString() : null,
                promo.getDiscountPercent(),
                promo.getDiscountAmountKopecks(),
                promo.getMinOrderKopecks(),
                popup.getCodeTtlDays(),
                repeated);
    }
}
