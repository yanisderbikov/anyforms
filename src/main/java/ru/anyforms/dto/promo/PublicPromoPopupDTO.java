package ru.anyforms.dto.promo;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.anyforms.model.payment.PromoCode;
import ru.anyforms.model.promo.PromoPopup;
import ru.anyforms.model.promo.PromoPopupType;

import java.util.UUID;

@Schema(description = "Попап с промокодом для витрины")
public record PublicPromoPopupDTO(
        UUID id,
        PromoPopupType popupType,
        String title,
        String description,
        String buttonText,
        String successTitle,
        String successText,
        Integer delaySeconds,
        @Schema(description = "Через сколько часов показывать снова; 0 — в каждый визит") Integer repeatAfterHours,
        @Schema(description = "Сколько раз показывать одному посетителю; null — без ограничений") Integer maxShows,
        Integer discountPercent,
        Long discountAmountKopecks,
        Long minOrderKopecks,
        Integer codeTtlDays,
        Boolean firstOrderOnly,
        @Schema(description = "Промокод для PUBLIC_CODE; null для CONTACT") String code,
        @Schema(description = "Окончание действия промокода PUBLIC_CODE (исключительно), ISO-8601") String codeValidUntil,
        @Schema(description = "Версия текста согласий, которую видит посетитель") String consentVersion,
        @Schema(description = "Не показывать, если посетитель уже вводил телефон или почту на сайте") Boolean hideForKnownContacts
) {
    public static PublicPromoPopupDTO forContact(PromoPopup p, String consentVersion) {
        return new PublicPromoPopupDTO(
                p.getId(),
                PromoPopupType.CONTACT,
                p.getTitle(),
                p.getDescription(),
                p.getButtonText(),
                p.getSuccessTitle(),
                p.getSuccessText(),
                p.getDelaySeconds(),
                p.getRepeatAfterHours(),
                p.getMaxShows(),
                p.getDiscountPercent(),
                p.getDiscountAmountKopecks(),
                p.getMinOrderKopecks(),
                p.getCodeTtlDays(),
                p.getFirstOrderOnly(),
                null,
                null,
                consentVersion,
                p.hidesKnownContacts());
    }

    public static PublicPromoPopupDTO forUniqueCode(PromoPopup p) {
        return new PublicPromoPopupDTO(
                p.getId(),
                PromoPopupType.UNIQUE_CODE,
                p.getTitle(),
                p.getDescription(),
                p.getButtonText(),
                p.getSuccessTitle(),
                p.getSuccessText(),
                p.getDelaySeconds(),
                p.getRepeatAfterHours(),
                p.getMaxShows(),
                p.getDiscountPercent(),
                p.getDiscountAmountKopecks(),
                p.getMinOrderKopecks(),
                p.getCodeTtlDays(),
                p.getFirstOrderOnly(),
                null,
                null,
                null,
                p.hidesKnownContacts());
    }

    public static PublicPromoPopupDTO forPublicCode(PromoPopup p, PromoCode promo) {
        return new PublicPromoPopupDTO(
                p.getId(),
                PromoPopupType.PUBLIC_CODE,
                p.getTitle(),
                p.getDescription(),
                p.getButtonText(),
                null,
                null,
                p.getDelaySeconds(),
                p.getRepeatAfterHours(),
                p.getMaxShows(),
                promo.getDiscountPercent(),
                promo.getDiscountAmountKopecks(),
                promo.getMinOrderKopecks(),
                null,
                promo.isFirstOrderOnly(),
                promo.getCode(),
                promo.getValidUntil() != null ? promo.getValidUntil().toString() : null,
                null,
                p.hidesKnownContacts());
    }
}
