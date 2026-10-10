package ru.anyforms.dto.promo;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.anyforms.model.promo.PromoPopupLead;

import java.util.UUID;

@Schema(description = "Заявка из попапа (админка)")
public record PromoPopupLeadDTO(
        UUID id,
        UUID popupId,
        String code,
        String email,
        String phone,
        String shopSlug,
        String deviceId,
        String consentVersion,
        String utmSource,
        String utmCampaign,
        Long amoLeadId,
        String createdAt
) {
    public static PromoPopupLeadDTO from(PromoPopupLead l) {
        return new PromoPopupLeadDTO(
                l.getId(),
                l.getPopupId(),
                l.getCode(),
                l.getEmail(),
                l.getPhone(),
                l.getShopSlug(),
                l.getDeviceId(),
                l.getConsentVersion(),
                l.getUtmSource(),
                l.getUtmCampaign(),
                l.getAmoLeadId(),
                l.getCreatedAt() != null ? l.getCreatedAt().toString() : null);
    }
}
