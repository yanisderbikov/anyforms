package ru.anyforms.dto.promo;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.anyforms.model.payment.PaymentProduct;
import ru.anyforms.model.payment.PromoCode;
import ru.anyforms.model.promo.PromoPopup;
import ru.anyforms.util.MoneyUtil;

import java.util.UUID;

@Schema(description = "Промокод на курс, выданный после покупки в магазине")
public record CoursePromoDTO(
        UUID popupId,
        @Schema(description = "Внутреннее название попапа — для аналитики") String popupName,
        String title,
        String description,
        String buttonText,
        String code,
        @Schema(description = "Код продукта курса") String productCode,
        @Schema(description = "Скидка, копейки") long discountKopecks,
        @Schema(description = "Цена курса без скидки, копейки") long coursePriceKopecks,
        @Schema(description = "Цена курса с промокодом, копейки") long discountedPriceKopecks,
        @Schema(description = "Окончание действия кода (исключительно), ISO-8601") String validUntil,
        Integer codeTtlDays,
        @Schema(description = "Последние 4 цифры телефона, к которому привязан код") String phoneTail,
        @Schema(description = "true — код по этому заказу уже выдавали раньше") boolean repeated
) {
    public static CoursePromoDTO of(PromoPopup popup, PromoCode promo, PaymentProduct product, boolean repeated) {
        long price = product.currentPriceKopecks();
        long discount = promo.getDiscountAmountKopecks() == null ? 0 : promo.getDiscountAmountKopecks();
        String phone = promo.getOwnerPhoneLast10();
        return new CoursePromoDTO(
                popup.getId(),
                popup.getName(),
                popup.getTitle(),
                popup.getDescription(),
                popup.getButtonText(),
                promo.getCode(),
                product.getCode(),
                discount,
                price,
                MoneyUtil.applyPromoDiscount(price, promo.getDiscountPercent(), promo.getDiscountAmountKopecks()),
                promo.getValidUntil() != null ? promo.getValidUntil().toString() : null,
                popup.getCodeTtlDays(),
                phone == null || phone.length() < 4 ? null : phone.substring(phone.length() - 4),
                repeated);
    }
}
