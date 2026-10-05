package ru.anyforms.dto.promo;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.anyforms.model.payment.PromoCode;
import ru.anyforms.model.promo.PromoPopup;
import ru.anyforms.model.promo.PromoPopupType;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Попап с промокодом (админка)")
public record PromoPopupDTO(
        UUID id,
        String name,
        @Schema(description = "CONTACT — персональный код за контакт, PUBLIC_CODE — готовый промокод для всех")
        PromoPopupType popupType,
        Boolean active,
        Integer priority,
        String shopSlug,
        String title,
        String description,
        String buttonText,
        String successTitle,
        String successText,
        Integer delaySeconds,
        @Schema(description = "Через сколько часов показывать снова; 0 — в каждый визит") Integer repeatAfterHours,
        @Schema(description = "Сколько раз показывать одному посетителю; null — без ограничений") Integer maxShows,
        @Schema(description = "ISO-8601; null — без нижней границы") String validFrom,
        @Schema(description = "ISO-8601, исключительная граница; null — бессрочно") String validUntil,
        Integer discountPercent,
        Long discountAmountKopecks,
        Long minOrderKopecks,
        String codePrefix,
        Integer codeTtlDays,
        Boolean firstOrderOnly,
        Long amoResponsibleUserId,
        Long amoTaskTypeId,
        Integer amoTaskDeadlineMinutes,
        @Schema(description = "Промокод из «Промокодов» для PUBLIC_CODE") UUID promoCodeId,
        @Schema(description = "Сводка по промокоду PUBLIC_CODE; null для CONTACT") PromoSummary promo,
        @Schema(description = "Не показывать тем, кто уже вводил телефон или почту на сайте") Boolean hideForKnownContacts,
        @Schema(description = "Сколько персональных кодов выдано") Long leadsCount,
        @Schema(description = "Сколько раз попап показали") Long viewsCount,
        @Schema(description = "Скольким устройствам попап показали") Long viewDevicesCount,
        @Schema(description = "Сколько оплаченных заказов с кодом попапа") Long usedCount,
        String createdAt,
        String updatedAt
) {
    @Schema(description = "Промокод, который показывает попап")
    public record PromoSummary(
            String code,
            Integer discountPercent,
            Long discountAmountKopecks,
            Long minOrderKopecks,
            Boolean firstOrderOnly,
            Boolean active,
            String validFrom,
            String validUntil,
            @Schema(description = "Работает ли код прямо сейчас") boolean currentlyValid
    ) {
        public static PromoSummary from(PromoCode p) {
            return new PromoSummary(
                    p.getCode(),
                    p.getDiscountPercent(),
                    p.getDiscountAmountKopecks(),
                    p.getMinOrderKopecks(),
                    p.isFirstOrderOnly(),
                    p.getActive(),
                    iso(p.getValidFrom()),
                    iso(p.getValidUntil()),
                    p.isCurrentlyValid());
        }
    }

    public record Stats(long leads, long views, long viewDevices, long used) {
        public static final Stats EMPTY = new Stats(0, 0, 0, 0);
    }

    public static PromoPopupDTO from(PromoPopup p, PromoCode promo, Stats stats) {
        return new PromoPopupDTO(
                p.getId(),
                p.getName(),
                p.getPopupType(),
                p.getActive(),
                p.getPriority(),
                p.getShopSlug(),
                p.getTitle(),
                p.getDescription(),
                p.getButtonText(),
                p.getSuccessTitle(),
                p.getSuccessText(),
                p.getDelaySeconds(),
                p.getRepeatAfterHours(),
                p.getMaxShows(),
                iso(p.getValidFrom()),
                iso(p.getValidUntil()),
                p.getDiscountPercent(),
                p.getDiscountAmountKopecks(),
                p.getMinOrderKopecks(),
                p.getCodePrefix(),
                p.getCodeTtlDays(),
                p.getFirstOrderOnly(),
                p.getAmoResponsibleUserId(),
                p.getAmoTaskTypeId(),
                p.getAmoTaskDeadlineMinutes(),
                p.getPromoCodeId(),
                promo != null ? PromoSummary.from(promo) : null,
                p.hidesKnownContacts(),
                stats.leads(),
                stats.views(),
                stats.viewDevices(),
                stats.used(),
                iso(p.getCreatedAt()),
                iso(p.getUpdatedAt()));
    }

    private static String iso(Instant instant) {
        return instant != null ? instant.toString() : null;
    }
}
